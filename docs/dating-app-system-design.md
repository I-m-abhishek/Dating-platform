# Dating App — Production System Design Deep Dive

Staff-level mock interview loop. Scale target: **100M MAU / 30M DAU** (Tinder/Hinge class).

For each question: what the interviewer is probing, a model answer at the staff bar, and the follow-up drill.

---

## Round 0 — Framing

> **Q1: "Design a dating app. 45 minutes. Go."**

**Probing:** whether you scope before drawing boxes. Starting with "I'll use Kafka and Cassandra" fails here.

**Functional scope, prioritized:**
1. Discovery — serve a deck of ranked candidates honoring filters
2. Swipe — like / pass / superlike, rate-limited by tier
3. Match — mutual like creates a durable match, notified in seconds
4. Chat — 1:1 messaging inside a match
5. Safety — block, report, unmatch, photo verification
6. Out of scope: monetization internals, group features, video calls

**The framing that carries the whole design:**

> **Not all writes are equal.** A left-swipe is high-volume, low-value — losing one means the user might see someone again in 90 days. A right-swipe is medium-value. A **match** is a durable business object. A **message** is sacred. Tier durability and consistency along that gradient rather than paying for strong consistency uniformly.

| Path | Availability SLO | Latency p99 | Durability |
|---|---|---|---|
| Deck fetch | 99.95% | 300 ms | N/A (recomputable) |
| Swipe: pass | 99.99% | 100 ms | Best-effort, 24h replayable |
| Swipe: like | 99.99% | 100 ms | Durable (quorum) |
| Match create → push | 99.99% | 5 s (p95) | 11 nines |
| Message send | 99.99% | 200 ms | Zero loss after ACK |

Availability beats consistency everywhere **except match creation and message ordering** — the two places to spend on linearizability.

**Follow-up:** *"Why 300ms for the deck?"* → The deck is prefetched and the swipe animation is client-side optimistic; latency is only perceived when the buffer empties. The real SLO is "buffer never empties," met by client prefetch at 10 remaining cards.

---

## Round 1 — Capacity Estimation

> **Q2: "Size it. Numbers I can check."**

```
Users:        300M registered / 100M MAU / 30M DAU
Swipes:       30M DAU x 60 swipes = 1.8B/day = 21K/s average
Peak:         4x (weekday 9pm local, "Dating Sunday" in January) -> 85K/s
              Design for 120K/s.
Right-swipe:  ~35% blended -> 630M likes/day
Matches:      ~1.5% of swipes -> 27M/day -> 310/s avg, ~1.2K/s peak
Messages:     ~150M/day -> 1.7K/s avg, 8K/s peak
Deck fetches: 30M x 4 sessions x 2 refills = 240M/day -> 2.8K/s avg, 12K/s peak
Concurrent WS connections: ~5M
```

**Storage:**
```
Swipes:  1.8B/day x ~48B packed = 86 GB/day -> 31 TB/yr -> ~95 TB/yr at RF=3
Matches: 27M/day x 200B = 5.4 GB/day  (trivial)
Messages:150M/day x 400B = 60 GB/day -> 22 TB/yr -> 66 TB at RF=3
Photos:  100M users x 6 photos x (1.5MB original + 400KB renditions) ~ 1.1 PB
```

**The punchline — derive the real bottleneck:**
```
Egress: deck of 30 profiles, prefetch 3 photos each @ 80KB AVIF = 7 MB/deck
        240M decks/day x 7 MB = 1.7 PB/day = ~160 Gbps avg, ~600 Gbps peak
```

> **The dominant cost and scaling problem in a dating app is image egress, not compute.** 120K/s of 48-byte writes is a *small* Scylla cluster. 1.7 PB/day of image bytes is a CDN contract negotiation and an AVIF migration.

Second-order: swipe storage grows 95 TB/yr forever → addressed by tiering (Round 7).

---

## Round 2 — API Design

> **Q3: "API for the swipe and deck path."**

```http
GET /v1/deck?limit=30&deck_token=<opaque>
-> 200 {
    cards: [{ user_id, display_name, age, distance_bucket, photos:[{url,blurhash}], prompts }],
    deck_token: "<HMAC-signed: recall_seed, served_ids, issued_at, geo_cell>"
  }
```

`deck_token` is server-signed and carries served-but-unswiped IDs. Three jobs: consistent pagination on refill, in-session dedup, and **anti-abuse** — a swipe on a target never served to you is rejected, killing scripted mass-liking bots.

```http
POST /v1/swipes
{
  client_swipe_id: "<UUIDv7>",     // idempotency key
  target_id: 12345,
  direction: "like" | "pass" | "super",
  deck_token: "...",
  client_ts: 1758600000123
}
-> 200 { accepted: true, match: { match_id, peer, created_at } | null }
```

**Two decisions worth defending:**

1. **Client-generated idempotency key.** Mobile networks retry. Without it, a retried like double-counts against the daily quota and can double-fire the match push. Dedupe: `SET NX swipe:idem:{id}` TTL 24h, plus a uniqueness constraint as backstop.

2. **Asymmetric batching:**
```http
POST /v1/swipes:batch    { swipes: [...] }   // passes only
```
Client buffers **passes** for up to 2s or 10 events. **Likes go immediately and alone** — the match result is returned synchronously and the "It's a Match!" animation must fire on the card just swiped. Since ~65% are passes, this cuts request count ~50% and hot-path QPS to ~42K/s.

**Push:** *"Why not make match detection fully async?"* → You can, and it's the **degraded mode** (Round 11). But in steady state an async match arrives 2–4 cards later, the user has moved on, and match-open rate drops measurably. Product-driven architecture decision — keep it behind a flag so you can fail over to async under load.

---

## Round 3 — Data Model & Storage Selection

> **Q4: "Pick your stores. Justify every one."**

| Data | Store | Key | Why |
|---|---|---|---|
| Profile / prefs / settings | **Postgres** (Vitess/Citus, sharded by `user_id`) | `user_id` | 100M small rows; `discoverable`, block list need **read-your-writes** + transactions. Low QPS. |
| Swipes | **ScyllaDB** | `PK=(actor_id)`, `CK=(target_id)` | 120K/s writes, append-only, no joins, TTL. Scylla over Cassandra for the shard-per-core C++ runtime — ~3–5x throughput/node, which matters at 95 TB/yr. |
| Inbound likes | **ScyllaDB** inverse table | `PK=(target_id, bucket)`, `CK=(ts DESC)` | Powers "See Who Liked You" + match detection. Bucketed — see hot partitions. |
| **Pair state** | **DynamoDB** (or Scylla LWT) | `PK=pair_key` | The atomic match primitive. Conditional writes. |
| Matches | **Postgres** | `PK=(low_id, high_id)` | Low volume, needs a unique constraint, relational queries. |
| Messages | **ScyllaDB** | `PK=(match_id)`, `CK=(seq DESC)` | One partition = one conversation, ordered reads. |
| Candidate index | **Custom in-memory geo-sharded service** | S2 cell | See Round 6. |
| Seen-set | **Redis** (roaring bitmaps) | `seen:{user_id}` | See Round 7. |
| Features / embeddings | **Redis + ANN index** | `user_id` | Ranking inputs. |
| Event bus | **Kafka** | varies | Shock absorber + fan-out to ML/analytics/safety. |

**The single most important schema decision — deterministic pair keys:**

```sql
CREATE TABLE matches (
  low_id      BIGINT NOT NULL,
  high_id     BIGINT NOT NULL,
  match_id    UUID NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL,
  state       SMALLINT NOT NULL,  -- active|unmatched|blocked|deleted
  PRIMARY KEY (low_id, high_id),  -- low_id < high_id, ALWAYS
  CHECK (low_id < high_id)
);
```

Canonicalizing the pair as `(min, max)` rather than `(actor, target)` makes both sides of a simultaneous mutual like target **the same row**. The unique constraint becomes the concurrency control: one INSERT wins, the other catches the duplicate-key violation and reads the existing row. **No distributed lock, no coordination service, no leader election.** Both users see the same `match_id`.

> That one line — `PRIMARY KEY (low_id, high_id)` — eliminates an entire class of race condition. Say it out loud; it signals you've built this before.

---

## Round 4 — The Swipe Write Path

> **Q5: "Walk me through `A likes B`, request to response, with your latency budget."**

```
Client --TLS--> CDN/Edge --> API Gateway --> Swipe Service --> {Redis, Dynamo, Scylla, Kafka}
```

```
1. AUTH + RATE LIMIT                                        ~3ms
   JWT verify (local, no network). Token bucket in Redis:
   free tier 100 likes/12h; premium unlimited but 1 swipe/200ms
   (bot ceiling - no human swipes faster).

2. IDEMPOTENCY                                              ~1ms
   SET NX swipe:idem:{client_swipe_id} = "pending" EX 86400
   If exists -> return cached response. Retries are free.

3. VALIDATE                                                 ~1ms
   HMAC-verify deck_token, assert target_id in served_ids,
   assert issued_at within 30 min.

4. INBOUND-LIKE PROBE (fast negative)                       ~1ms
   GETBIT inbound_bitmap:{A} @ dense_id(B)
   98.5% of the time this is 0 -> skip step 5 entirely.

5. ATOMIC PAIR UPDATE (only if probe hit)                   ~8ms
   DynamoDB UpdateItem, pair_key = (min(A,B), max(A,B)):
     SET liked_by_low = true (or liked_by_high)
     ReturnValues: ALL_NEW
   If liked_by_low AND liked_by_high AND no block flag
   -> I completed the mutual.

6a. MATCH PATH (1.5%)                                       ~12ms
    INSERT INTO matches ... ON CONFLICT DO NOTHING RETURNING *
    Produce MatchCreated -> Kafka (acks=all)
    Return { match: {...} } to A. Async consumer pushes to B.

6b. NO-MATCH PATH (98.5%)
    Write swipe -> Scylla, CL=LOCAL_QUORUM        ~6ms
    SETBIT inbound_bitmap:{B} @ dense_id(A)       ~1ms
    Produce SwipeRecorded -> Kafka (acks=1)
    Return { match: null }

TOTAL p50 ~ 18ms, p99 ~ 75ms
```

**For `direction=pass`:** skip steps 4–6. Write to Scylla at `CL=ONE` with the batch, Kafka at `acks=1`. ~5ms. Losing a pass in an AZ failure means the user sees that profile again in a few weeks — state that tradeoff to the PM explicitly.

**Kafka topics:**

| Topic | Partition key | Consumers |
|---|---|---|
| `swipes` | `actor_id` | Elo/desirability updater, feature pipeline, fraud detection, Iceberg sink |
| `matches` | `match_id` | Notification service, safety scoring, analytics |
| `messages` | `match_id` | Content moderation, notification, engagement metrics |
| `profile_updates` | `user_id` | Candidate index updater, search reindex, cache invalidation |

**Follow-up:** *"Step 5 is a conditional write at 42K/s. Expensive?"* → The step-4 bitmap probe is why it isn't. One Redis `GETBIT` (~0.3ms) returns false 98.5% of the time, so you do ~1.5K conditional writes/s, not 42K. The bitmap is a **cache, not a source of truth** — if Redis is cold, fall back to always executing step 5: slower but correct. **Never let an optimization become a correctness dependency.**

---

## Round 5 — Race Conditions (make-or-break)

> **Q6: "A and B swipe right on each other at the same millisecond, different servers, different AZs. What happens?"**

**This is THE question.** Get it wrong and you're a mid-level hire.

**The naive failure:** both servers do read-then-write. Both read "peer hasn't liked me" (true at read time), both write their like, neither detects the mutual. **Lost match, silently, forever.** No error, no alert, no complaint — they just never meet. The worst kind of bug: invisible.

### (1) Atomic conditional update on a canonical pair row — preferred

```
pair_key      = "42:9001"        // always (min:max)
liked_by_low  = bool
liked_by_high = bool
blocked       = bool
matched_at    = timestamp | null
```

```python
resp = ddb.update_item(
    Key={'pair_key': pair_key},
    UpdateExpression='SET liked_by_low = :t, low_ts = :now',
    ConditionExpression='attribute_not_exists(blocked) OR blocked = :f',
    ReturnValues='ALL_NEW')

item = resp['Attributes']
i_completed_the_match = (item['liked_by_low'] and item['liked_by_high']
                         and item.get('matched_at') is None)
```

The read-modify-**read-back** is one atomic op on one item. No window. Both racers serialize at the storage layer; the second sees both flags true. Only one sees `matched_at is None`, so **exactly one** creates the match and fires notifications — which also solves duplicate pushes.

### (2) Partition-by-pair ordering (if you lack cheap CAS)

Produce every swipe to Kafka keyed by `pair_key`. Both directions land on the same partition, consumed by one thread, in order. The second sees the first. Cost: match detection becomes async (~200ms–2s), so the sync animation is gone.

### (3) Reconciliation job — ship this regardless

Hourly Spark/Flink job over the swipe log: find pairs where both directions are `like`, `matched_at` is null, no block. Emit missing matches. Catches bugs, degraded-mode gaps, and partial failures.

> **Why not Cassandra LWT?** Paxos round-trips make it ~4x a normal write and it degrades under contention. Here contention is near-zero (2 participants/key) so it *would* work — but DynamoDB conditional writes give the same semantics at lower operational cost. If staying single-vendor on Scylla, LWT on the pair table is acceptable at 1.5K/s.

---

> **Q7: "There's a window between your pair update and your match INSERT. What if the service crashes there?"**

That's the dual-write problem. The pair row says "mutual" but no match row exists — neither user is notified.

**Fix — transactional outbox / state machine on the pair row:**

The pair row carries `match_state in {none, pending, created}`. The conditional update sets `pending` atomically with the flag flip. A durable workflow (Temporal, or a Kafka consumer state machine) drives `pending -> created`, **idempotent** thanks to the `(low_id, high_id)` unique constraint. If the process dies, a sweeper picks up rows stuck in `pending > 10s`. Notification fires only after `created`, deduped by `match_id`.

At-least-once delivery + idempotent operations + a deterministic key = effectively-once. Better than a distributed transaction.

---

> **Q8: "Rewind — undo my last swipe. What breaks?"**

1. **Undo a pass:** delete the swipe row, clear the bit in the seen bitmap. **This is why roaring bitmaps beat Bloom filters** — Bloom can't delete.
2. **Undo a like that didn't match:** flip `liked_by_low/high` false, clear `inbound_bitmap:{B}`, delete the swipe.
3. **Undo a like that *did* match:** the peer was notified, may have opened the chat, may have sent a message. You can't unring that bell. **Product decision:** rewind is disallowed once a match exists. Return `409 REWIND_UNAVAILABLE_MATCHED`. Surface this to product before building — silently destroying the peer's match generates trust-destroying tickets.

Implementation: last 5 swipes per user in a Redis list, 1h TTL. Premium feature, low volume, slow correct path is fine.

---

## Round 6 — The Recommendation Engine

> **Q9: "Deck fetch. 100M users, 30 good ones, 300ms."**

Two-stage: **recall** (100M -> ~2,000) then **rank** (2,000 -> 30). Never score 100M.

### Stage 1: Recall (target 50ms)

```
distance <= A.max_distance
A.age in B.age_range   AND   B.age in A.age_range        <- bidirectional!
A.gender in B.seeking  AND   B.gender in A.seeking       <- bidirectional!
B.active_within(14d) AND B.discoverable AND B.not_banned
B not in A.seen_set  AND  B not in A.blocked  AND  A not in B.blocked
```

> **The bidirectional preference constraint is the subtle part.** Most candidates write a one-sided filter and get caught. A 45-year-old man who wants 25-year-olds must not appear in a 25-year-old's deck if she wants 24–32. The index must be queryable on **the candidate's own preferences**. Encode each cell's population as bitsets (`wants_female`, `wants_male`) plus age-bucketed posting lists, so the mutual constraint is a bitset AND, not a scan.

**Geo: S2 cells, not geohash.** S2 cells have near-uniform area (geohash distorts toward the poles with ugly boundary behavior), and `S2RegionCoverer` gives a compact cell set for a radius query. Index at ~level 12 (~3–5 km²); a 50 km radius covers a few hundred cells.

**Custom in-memory index, not Elasticsearch** — sizing justifies it:
```
Profile-lite: user_id(8) + lat/lng(8) + age(1) + gender/seeking bitset(2)
            + active_at(4) + quality(2) + embedding_id(4) + flags(2) ~ 32B
            + posting-list overhead ~ 200B
100M x 200B = 20 GB total -> ~12 nodes at RF=3, fully in RAM
```
The entire searchable universe is 20 GB. ES would work and is right for a 20-person team, but a purpose-built service gives 5–10ms recall vs 50–80ms — at 12K deck QPS that's the difference between hitting and missing 300ms p99. **State both options and the team-size tradeoff.**

**Blended recall sources:**

| Source | Share | Purpose |
|---|---|---|
| Geo + filters, quality-sorted | 60% | Bread and butter |
| **Inbound likes** | 15% | Highest conversion — guaranteed match if swiped right |
| Embedding ANN (taste-similar) | 15% | Personalization beyond demographics |
| New / under-exposed users | 7% | Cold start + marketplace health |
| Exploration (epsilon-greedy) | 3% | Breaks filter bubbles, generates training data |

### Stage 2: Ranking (target 120ms)

**The objective function is the whole interview:**

```
score(A, B) = P(A likes B)
            x P(B likes A | A likes B)     <- reciprocity
            x P(conversation >= 3 turns)   <- the actual product goal
            x exposure_penalty(B)          <- marketplace fairness
```

> Optimizing `P(A likes B)` alone is the classic failure. It converges on showing everyone the most conventionally attractive ~1%, who get 10,000 unread likes and churn, while everyone else gets zero matches and churns. **The marketplace collapses.** Optimize for *mutual* interest and enforce an exposure budget per candidate per day. An economics problem wearing an ML costume.

Model: two-tower for retrieval embeddings (trained on mutual-like pairs), GBDT or shallow DNN for reranking. ~40 features: distance, age delta, activity recency, photo quality, profile completeness, historical give/receive like rates, embedding cosine, reply rate, time-of-day, session position.

**Latency budget:**
```
Edge + auth + gateway                 20ms
Recall fan-out (hedged, 2 replicas)   30ms
Seen-set intersect (1 Redis GET)       2ms
Feature fetch (batched mget)          40ms
Model inference (batch of 2000)       80ms
Hydration: photo URLs, signing, CDN   40ms
Serialization + slack                 88ms
-----------------------------------------
                             p99 = 300ms
```

**Hybrid precompute:** background job refreshes a warm pool of ~500 candidates per active user every 4h into Redis. Deck fetch ranks from the warm pool when fresh and filters unchanged, else falls through to live recall. Covers ~70% of requests at ~60ms and is a free fallback when recall is degraded.

---

> **Q10: "What's the Elo score, and would you build it?"**

Historically Tinder ran an Elo-style desirability scalar: liked raises, passed lowers, **weighted by the swiper's own score**. Streaming job off `swipes` into the feature store.

Build the *signal*, not a single exposed scalar:
- A scalar creates a strict global hierarchy and a rich-get-richer loop
- It encodes and amplifies demographic biases in swipe data — real legal and PR exposure
- Brittle: one bad photo tanks you permanently

Modern practice: **learned embeddings + per-viewer personalized scores**. Attractiveness isn't a scalar, it's a vector, and viewers weight it differently. Keep a coarse "engagement quality" bucket for exposure balancing; let the ranker learn the rest per-viewer. Raising fairness unprompted is a strong staff signal.

---

> **Q11: "Small town. 400 eligible users. Day three, deck is empty."**

**Candidate exhaustion** — the #1 retention killer outside dense metros. `deck_empty_rate` should be a top-line SLI, segmented by geo.

Automatic escalation ladder when recall drops below threshold:
1. **Auto-expand radius** — 50 -> 100 -> 250 km, with a UI hint
2. **Relax soft filters** — widen age band +/-2, then +/-5
3. **Resurface passed profiles** — a pass 90 days ago isn't a permanent verdict (exactly why the seen-set has time-bucketed pass expiry)
4. **Relax activity filter** — 60 days instead of 14
5. **Surface unacted inbound likes**
6. **Be honest** — an empty state with "we'll notify you" beats padding the deck with garbage. Padding trains users that the deck is worthless.

Supply-side fix: growth spend must be **geo-targeted and gender-balanced**, not national. 400 users isn't an engineering problem, it's market entry — and the eng answer is making that visible in dashboards.

---

## Round 7 — The Seen-Set

> **Q12: "Never show the same person twice. A power user has 80,000 swipes."**

Naive: read all swipes per deck request. 80K rows at 12K deck QPS = ~1B row reads/s. Dead.

**Reframe:** you never need A's *entire* seen-set — only which of the **2,000 recalled candidates** A has seen. That's a set intersection, not enumeration.

**Solution: roaring bitmaps over dense integer IDs.** Assign a dense `int32` at signup, stored in the profile row.

```
seen:{user_id} -> RoaringBitmap<int32>

Median user (2K swipes):   ~4 KB
p99 user (80K swipes):     ~90 KB
Average:                   ~10 KB
100M users x 10 KB       = 1 TB total
```

1 TB over a 40-node Redis cluster at 32 GB usable is comfortable, with hot/cold tiering: only ~30M DAU bitmaps resident (~300 GB hot). Cold users rehydrate from Scylla on session start (one partition scan, ~50ms, once per session).

Deck path: one `GET seen:{A}` (~10 KB, sub-ms), deserialize, `AND NOT` against candidates.

**Why roaring over Bloom:** Bloom at 1% FPR and 80K items is ~96 KB — *no smaller* — and false positives **permanently and silently hide real people**. Over a 2,000-candidate recall that discards ~20 valid candidates per deck, forever, undetectably. Roaring is exact, comparably sized, and supports deletion (required for rewind). Bloom is the wrong tool; push back if proposed.

**The TTL nuance:**
```
seen:liked:{uid}        -> permanent
seen:matched:{uid}      -> permanent
seen:blocked:{uid}      -> permanent
seen:passed:{uid}:{wk}  -> weekly buckets, drop buckets older than 13 weeks
```

Passes expire — both a product decision (fixes exhaustion in thin markets; taste changes) and what keeps storage bounded. The deck query unions active pass buckets; roaring OR is fast.

**Storage tiering this enables:** Scylla swipe rows get `TTL 90d` for passes, no TTL for likes. Converts 95 TB/yr unbounded growth into ~12 TB steady state for passes plus ~33 TB/yr for likes — the ones with business value.

---

## Round 8 — Chat

> **Q13: "5M concurrent connections, messages never out of order or missing."**

### Connection layer
```
5M concurrent / 150K per node (tuned Go or Netty) = ~35 nodes + 50% headroom = 50 nodes
```

Gateways stateless except the registry: `conn:{user_id} -> {gateway_id, conn_id}` in Redis, 45s TTL refreshed by heartbeat. Gateway death -> TTL expiry -> clients reconnect with exponential backoff **plus jitter**. Without jitter, one gateway dying reconnects 150K clients in the same 100ms and takes down the next gateway — classic thundering-herd cascade.

### Send path
```
client -> gateway -> chat-svc
  1. authz: is sender a participant in an ACTIVE match?   <- re-check, don't trust client
  2. dedupe on client_msg_id
  3. assign seq = INCR seq:{match_id}                     <- ordering
  4. INSERT INTO messages (match_id, seq, ...) CL=LOCAL_QUORUM
  5. ACK to sender with assigned seq                      <- ACK only after durable write
  6. lookup conn:{recipient} -> deliver over WS
     (offline -> Kafka -> push notification)
```

**Ordering: per-match monotonic sequence, never timestamps.** Clock skew reorders messages and users notice instantly — a reply above the question. A conversation has 2 participants, so `INCR seq:{match_id}` contention is ~zero. If Redis loses the key, recover with `SELECT MAX(seq) FROM messages WHERE match_id = ?` and reseed.

**Gap-free sync:** client stores `last_seq` per match; on reconnect `GET /matches/{id}/messages?after_seq=N`. Because `seq` is dense and monotonic the client can *prove* completeness — holding 1..40 and 42, it knows 41 is missing. Impossible with timestamps or UUIDs.

**Exactly-once from the user's perspective:** at-least-once delivery + `client_msg_id` dedupe + idempotent server assignment. Client renders optimistically with a temp ID, reconciles to server `seq` on ACK.

### Trip-ups
- **Read receipts:** store `last_read_seq` per `(match_id, user_id)` — one updatable row. A row per message read is 10x write amplification for zero value.
- **Typing indicators:** pub/sub only, never persisted, rate-limited to 1 event/3s. Loss is invisible.
- **Unmatch:** soft-delete (`state = unmatched`), messages immediately inaccessible to *both* via read-time authz, hard-deleted async after retention. **Irreversible and instant** — it's a safety feature, and latency there is a safety failure.
- **Media in chat:** presigned upload -> moderation scan -> *then* deliverable. Never deliver unscanned media; CSAM and unsolicited explicit content are existential risks, not edge cases.

**Honest scale note:** 8K msg/s is genuinely small — one tuned Scylla cluster handles it. The hard parts are 5M idle connections, reconnect storms, and ordering. Conflating connection scale with message scale is a common error.

---

## Round 9 — Media & Moderation

> **Q14: "Photo upload pipeline."**

```
1. POST /v1/media/upload-url -> presigned S3 PUT into an UPLOADS bucket
   (never the serving bucket - unscanned content must never be publicly reachable)
2. S3 event -> Kafka -> processing DAG:
     |- STRIP EXIF                    <- GPS in photo metadata leaks home
     |                                   addresses. Safety-critical, non-negotiable.
     |- Malware scan
     |- NSFW / CSAM classifier        <- hard block, mandatory reporting path
     |- Age-estimation model          <- underage detection
     |- Face detection                <- is there a face? exactly one person?
     |- Perceptual hash (pHash)       <- match vs known-stolen / celebrity /
     |                                   previously-banned corpus (catfish)
     |- Quality score                 <- feeds ranking
     |- Renditions: AVIF + WebP @ 4 sizes, blurhash placeholder
3. All pass -> copy to SERVING bucket -> CDN -> mark photo active
4. Any fail -> quarantine + human review queue
```

**Egress cost (the dominant cost from Round 1):**
- AVIF over JPEG: ~50% smaller at equal perceptual quality — a very large number on 1.7 PB/day
- Blurhash placeholders: 30 bytes inline, card renders instantly, defer the full image
- Prefetch only the **first** photo for cards 2–30; fetch 2–6 on tap. Cuts deck egress ~60%
- Signed URLs with a rotating **path-level** key (not per-user) so URLs aren't scrapeable but stay CDN-cacheable. Per-user signing fragments the cache key and destroys hit rate.

**Photo verification** (pose-match selfie -> face embedding comparison): worth building, measurably reduces catfishing, strong trust signal. But face embeddings are **biometric data**: GDPR Art. 9 special category, plus Illinois BIPA and Texas CUBI with per-violation statutory damages. Requires explicit separate consent, encrypted storage with a distinct key, defined retention, per-jurisdiction gating. Flag in design review, not after legal finds it.

---

> **Q15: "Someone is harassing users. How does your system know?"**

| Layer | Mechanism | Latency |
|---|---|---|
| Pre-send | Text classifier; high-confidence violations held | <100ms, inline |
| Post-send | Async scan, retroactive removal + account action | seconds |
| Behavioral | Report rate per 1000 matches, unmatch-within-60s rate, message-to-reply ratio | minutes |
| Graph | Device ID / payment instrument / IP clusters -> bot rings | hours |
| Human | Report queue, prioritized by predicted severity x reporter credibility | minutes–hours |

**Bot signals that work:** 100% right-swipe rate (no human likes everyone), swipe intervals with near-zero variance, identical opening messages across matches, immediate off-platform solicitation, account created and 500 swipes within 10 minutes.

**Block must be enforced at four points** — missing any one is a safety incident:
1. Recall filter (never appears in the deck)
2. Match creation (the `blocked` flag — a block can land *between* the two likes)
3. Chat authz on every send AND every read
4. Push notification suppression

---

## Round 10 — Multi-Region, Data Residency, Passport

> **Q16: "EU data stays in the EU. Also users travel. Reconcile."**

**Core insight — split the sharding dimension:**

> **Shard user-owned data by `user_id` with a pinned home region. Shard the candidate index by geography.** Different systems, different keys. Conflating them is the mistake.

```
PII, swipes, matches, messages  ->  home_region (EU / US / APAC), by user_id
Candidate index                 ->  geo-sharded by S2 cell, deployed near the geography
```

A German user in Tokyo:
- Deck query hits the **APAC candidate index** (fast, local, correct inventory)
- Swipe writes go to the **EU home region** (~250ms cross-region — client is optimistic, so the animation is instant; only the match result is delayed)
- PII never leaves the EU

This also cleanly solves **Passport** — same code path with a manually-set location. No special casing.

**Cross-region matches** — the genuinely messy part; say so rather than hand-wave. A German matches a Japanese user; the match needs one authoritative home. Rule: **the match lives in the region of `low_id`** — deterministic, no coordination. The other region holds a lightweight pointer row so it appears in both match lists. Messages live with the match, so one participant always has higher chat latency. Mitigations: a read-replica of the message partition in the peer region; accept ~150–250ms send latency for the remote party. Own the asymmetry.

**Failure domains:**
- Within region: 3 AZs, RF=3, `LOCAL_QUORUM` -> survives full AZ loss, zero data loss, no failover
- Region loss: async cross-region replication, **RPO ~5–30s, RTO ~10–15 min**. State it: you will lose up to 30s of swipes. Matches and messages replicate on a tighter dedicated stream (high-value tier).
- **Cell-based architecture:** within a region, hash users into ~10 isolated cells, each a full vertical stack. A bad deploy or poison-pill request hits 10% of users, not 100%. Deploy cell-by-cell with automated rollback on SLO regression.

---

## Round 11 — Availability Engineering

> **Q17: "Your ranking service is completely down. What does the user see?"**

**A deck.** Every dependency needs a defined, tested fallback:

| Dependency down | Degraded behavior | User impact |
|---|---|---|
| Ranking / ML inference | Recall output sorted by `(active_recently, distance)` | Quality drops; app works |
| Feature store | Model runs on default/median features | Slightly worse ranking |
| Candidate index | Serve precomputed warm pool from Redis | Staler candidates, correct behavior |
| Redis seen-set | Deck without dedup; log for repair | Some repeats. **Better than no deck.** |
| DynamoDB pair table | Queue likes to Kafka; async match + push | Match in ~5s instead of instantly |
| Scylla swipes | Kafka only; replay on recovery | Invisible to the user |
| Kafka | Local disk-backed buffer, drain on recovery | Analytics/ML lag |
| Chat service | Deck + swipe still work; chat shows "reconnecting" | Partial outage, correctly scoped |

> **Principle: never fail a swipe.** The swipe is the app's heartbeat. Every downstream failure degrades into "accept the write durably somewhere, reconcile later." A user who can't swipe closes the app; a user whose match arrives 5s late doesn't notice.

**Name these specifically:**
- **Hedged requests** on recall fan-out — fire to 2 replicas at p95, take the first. Cuts tail latency hard for ~5% throughput.
- **Adaptive concurrency limits** (TCP-Vegas style, Netflix `concurrency-limits`) over fixed thread pools — self-tunes to actual downstream capacity.
- **Load shedding by priority:** swipe writes > deck > profile edits > analytics > batch.
- **Retry budgets:** retries capped at 10% of base traffic, circuit-broken per dependency. Uncapped retries turn a brownout into an outage — retry storms are the most common way partial failure becomes total.
- **Jitter on everything** — reconnects, retries, cron, cache TTLs.

---

> **Q18: "Define durability. What actually threatens it?"**

Disk failure is **solved**: RF=3 across AZs + continuous commitlog archiving to object storage + PITR. Test restores quarterly — an untested backup is a hypothesis.

**The real threats, by likelihood:**

1. **Logical corruption from a bad deploy** — a migration that nulls `match.state`, a consumer bug that deletes matches. Replication faithfully replicates corruption to all 3 replicas instantly. Defenses: soft deletes with 30-day tombstones, append-only audit log, rebuild derived state by replaying Kafka, canary deploys with data-integrity assertions.
2. **Operator error** — `DELETE` without `WHERE`. Defense: no direct production write access; all mutations through reviewed, audited tooling.
3. **Silent loss from misconfigured consistency** — someone changes `LOCAL_QUORUM` to `ONE` for latency and loses writes during node flaps. Defense: consistency levels as reviewed config-as-code, asserted in integration tests.
4. **Cascading GDPR deletion bug** — a saga with a bad user-ID filter erases live accounts. Defense: dry-run mode, per-store deletion receipts, blast-radius caps (a job touching >1000 users halts and pages).

All four are **software** failures. Replication factor helps with none of them. That's the answer that shows you've been on-call.

---

## Round 12 — Consistency Curveballs

> **Q19: "I change my distance filter from 5 km to 50 km. When does my deck update?"**

Immediately — next deck fetch. **Preferences are read from the strongly-consistent profile store at deck time, never from a cached deck.** The `deck_token` carries `prefs_version`; a mismatch invalidates it and forces fresh recall. The warm pool is invalidated on the `profile_updates` event. Get this wrong and the user widens their radius, sees the same 5 people, concludes the app is broken — a top-5 support ticket category.

> **Q20: "I delete my account. Every place my data lives?"**

Two distinct phases — conflating them is the error.

**Phase 1 — immediate (<5s), availability-critical:** set `discoverable = false`, publish to `profile_updates`. Candidate index drops the user within seconds. Active matches -> `state = deleted`, chat inaccessible. **From every other user's perspective, you are gone.** Must be fast and must not depend on the erasure pipeline.

**Phase 2 — erasure (within 30 days, GDPR Art. 17), a durable saga:**

| Store | Action |
|---|---|
| Profile (Postgres) | Hard delete; retain `deleted_user_id` tombstone only |
| Swipes — outbound | Delete partition `actor_id = me` |
| Swipes — **inbound** | Delete my ID from **every peer's** partition ← the expensive one; drive from my outbound log |
| Pair table | Delete all rows containing my ID |
| Matches | Delete rows; peers' match lists updated |
| Messages | **Anonymize, don't delete** — see below |
| Media | Delete S3 objects + **CDN purge** (easy to forget; cached objects stay publicly fetchable) |
| Candidate index | Evict |
| Feature store / embeddings | Delete; exclude from next training run |
| Analytics warehouse | Delete or irreversibly pseudonymize |
| Backups | Cannot surgically delete → documented 35-day rotation + "restore-and-re-delete" runbook |

Orchestrate with **Temporal** (or equivalent durable workflow): one activity per store, each idempotent, each emitting a receipt. The regulator's question is "prove it," so receipts are the deliverable, not the deletion.

**Messages subtlety:** messages you sent are *also the other user's* personal data, part of their conversation history and potentially their evidence in a harassment case. Standard practice is **anonymize** (`sender -> "Deleted User"`, PII scrubbed) with a documented legitimate-interest basis. Raise this as a legal question with a recommendation rather than deciding unilaterally — "I'd escalate to counsel with a recommendation" beats a confident wrong answer.

> **Q21: "A celebrity signs up and gets 2 million inbound likes. What breaks?"**

The inbound-likes table: `PK = target_id` means 2M rows in one partition. Scylla/Cassandra degrade past ~100 MB/partition — compaction stalls, reads time out, the hosting node becomes a hotspot that can take down the shard.

**Fix — composite bucketed partition key:**
```
PK = (target_id, bucket)   where bucket = hash(actor_id) % 64
CK = (ts DESC, actor_id)
```
Writes spread across 64 partitions. "Who liked me" scatter-gathers across all 64 and merges — fine for a paginated UI. Bucket count can be adaptive: start at 1, promote to 64 when inbound-like velocity crosses a threshold tracked in the profile row.

**Second-order:** that account distorts the ranker (everyone's top candidate), accumulates 2M unread likes they'll never process, and wastes exposure. Exactly why the **exposure budget** exists — and why verified-celebrity accounts usually get separate discovery treatment.

---

## Round 13 — Rapid Fire

**"Why not Neo4j for matches?"** — Matches are a trivial graph: one edge type, no traversal deeper than 1 hop. A graph DB buys nothing and costs operational maturity and horizontal scale. Use one when you need multi-hop traversal (friend-of-friend recall, fraud-ring detection) — a *separate offline* system, not the serving path.

**"Why Scylla over DynamoDB for swipes?"** — At 120K writes/s sustained, DynamoDB per-request pricing dominates; Scylla on reserved instances is roughly an order of magnitude cheaper. Keep DynamoDB for the pair table specifically because it's low-volume (~1.5K/s) and you want managed conditional writes. Different tool per workload shape — state the cost crossover point rather than arguing style.

**"Clock skew?"** — Never order by wall clock. Per-match `seq` for messages, `pair_key` CAS for matches, UUIDv7 client IDs for idempotency. Wall-clock timestamps are for display only, client-side corrected.

**"What breaks first at 10x (300M DAU)?"** — (1) CDN egress cost, well before any technical limit; (2) the Redis seen-set cluster at 10 TB — move to a disk-backed store with an in-memory hot tier; (3) ranker inference cost — distillation + more aggressive warm-pool caching; (4) Scylla swipe storage — more aggressive pass TTLs. Swipe write throughput at 1.2M/s is still fine — the least scary number on the list.

**"One thing you'd cut for a v1 with 5 engineers?"** — The custom in-memory candidate index. Use Postgres + PostGIS or Elasticsearch, accept 80ms recall, ship in 6 weeks. Build the custom index when you have a *measured* latency problem, and keep the recall interface stable so swapping it is a one-service change. But do **not** cut the `(low_id, high_id)` pair key or the idempotency key — one-line decisions that are extremely expensive to retrofit.

---

## The Five Things That Decide This Interview

1. **Tiered durability** — passes ≠ likes ≠ matches ≠ messages. Say it in the first five minutes.
2. **`PRIMARY KEY (low_id, high_id)` + atomic pair CAS** — the simultaneous-mutual-like race. This is *the* question.
3. **Bidirectional preference filtering** in recall. Almost everyone writes the one-sided filter.
4. **Optimize for reciprocity + exposure budgets**, not `P(A likes B)`. Marketplace collapse is the interesting failure.
5. **A written graceful-degradation matrix** — never fail a swipe.
