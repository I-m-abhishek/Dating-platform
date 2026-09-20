# Architecture

## Shape of the system

```
Browser (Next.js)
   │  REST  (JSON, Bearer JWT)
   │  STOMP (WebSocket: chat, notifications, call signalling)
   ▼
Spring Boot API ──► PostgreSQL          (all durable state)
       │        ──► Local filesystem    (media, behind a StorageService interface)
       │
       └──► Schedulers: auto-match, standouts, subscription expiry, media cleanup

Browser ◄──── WebRTC peer connection ────► Browser     (call audio/video, never via the API)
```

Single deployable backend, single frontend. Nothing here needs a message broker, a search
cluster or a cache server to work correctly — each of those has a documented seam if the
load ever justifies it.

---

## Backend layering

Feature-sliced, layered inside each slice:

```
com.dating.platform
├── common/          response envelope, error codes, exception handling, base entities
├── config/          typed configuration, security, WebSocket, OpenAPI, caching
├── security/        JWT issuing and verification, principal, STOMP auth
├── ratelimit/       @RateLimit annotation, interceptor, token buckets
├── util/            geo and date helpers
│
├── auth/            register, login, refresh token rotation
├── user/            account, preferences, location, onboarding
├── profile/         profile content, photos, prompts, reference data
├── discovery/       candidate search and the home feed
├── interaction/     likes, passes, the Likes You tab
├── match/           matches, the compatibility engine, auto-match
├── chat/            conversations, messages, attachments
├── call/            call sessions and WebRTC signalling
├── comment/         photo comments
├── standout/        the Standouts shelf and its ranking
├── subscription/    plans, subscriptions, entitlements
├── quota/           metered allowances
├── media/           storage abstraction, uploads
├── notification/    in-app notifications
└── safety/          blocking and reporting
```

Inside a slice:

| Layer | Responsibility | Never does |
| --- | --- | --- |
| `controller` | HTTP shape, validation, auth annotations, rate limits | Business rules, transactions |
| `service` | Business rules, transaction boundaries, orchestration | Know about HTTP |
| `repository` | Queries | Contain rules |
| `entity` | Persistent model | Leave the service layer |
| `dto` | Wire contracts | Contain behaviour beyond simple derivation |

**The one rule that keeps this honest:** entities never cross the controller boundary. Every
response is a record in `dto`. That is why, for example, `AutoMatchRun` — an audit row — is
mapped to `AutoMatchRunResponse` rather than serialised directly.

### Cross-slice dependencies

Slices depend downwards, never sideways-and-back:

```
discovery ──► match.engine, interaction, profile, safety
match     ──► chat (creates the conversation), profile, safety, notification
chat      ──► match, media, safety, notification
comment   ──► profile, quota, subscription, safety
standout  ──► profile, interaction, match, safety
everything──► quota, subscription, safety, common
```

`safety`, `quota`, `subscription` and `common` are leaves. Nothing depends on a controller.

---

## The request lifecycle

```
1. CorsFilter
2. JwtAuthenticationFilter      -> UserPrincipal in the SecurityContext (no DB hit)
3. SecurityFilterChain          -> route-level authorisation
4. RateLimitInterceptor         -> @RateLimit, or a coarse default
5. @Valid on the request body   -> MethodArgumentNotValidException on failure
6. Controller                   -> delegates immediately
7. Service                      -> @Transactional, business rules, quota and entitlement checks
8. Repository                   -> query
9. ApiResponse<T>               -> uniform envelope
   GlobalExceptionHandler       -> uniform envelope on failure
```

Anything thrown below step 6 lands in `GlobalExceptionHandler`, which is the only place that
decides an HTTP status. Business code throws a `BusinessException` carrying an `ErrorCode`;
it never touches `ResponseEntity`.

---

## Data model

Thirty tables. The ones with a design decision in them:

### `users` + `profiles`

Split deliberately. Discovery runs against `users` and needs only date of birth, gender and
coordinates; keeping the essay fields in a separate table keeps that hot query narrow.

### `matches`

`user_a_id` is always the lexicographically smaller UUID, enforced by a check constraint.
That canonical ordering lets a single unique index guarantee "one match per pair" — the
alternative, storing the pair in swipe order, permits duplicates under concurrency.

`conversations.match_id` and `matches.conversation_id` point at each other. The write order
(match → conversation → update match) keeps both FKs satisfiable.

### `usage_counters`

One row per `(user, feature, period_key)`. `period_key` is a date (`2026-09-19`) or an ISO
week (`2026-W38`). Consumption is a single conditional `UPDATE`:

```sql
UPDATE usage_counters SET used = used + 1
WHERE user_id = ? AND feature = ? AND period_key = ? AND used < ?
```

Zero rows updated means the allowance is spent. Check and increment in one statement is what
makes "five comments a day" correct under concurrent requests.

### `auto_match_runs`

One row per `(user, cadence, period_key)`, unique. This is the idempotency key for the whole
auto-match feature — see [`matching.md`](matching.md).

### `standout_snapshots`

A whole ranking is written under a new `cycle_key`, then the previous cycle is deleted.
Readers always see a complete ranking; a delete-then-insert would expose a half-built shelf.

### `likes`

Unique on `(sender_id, receiver_id)` with a check that they differ. The reciprocal pair is
what creates a match; both rows flip to `MATCHED` in one transaction.

---

## Real-time

STOMP over SockJS at `/ws`.

| Destination | Direction | Carries |
| --- | --- | --- |
| `/topic/conversations/{id}` | server → clients | messages, typing, read, deletions |
| `/user/queue/notifications` | server → one user | new like, new match, new message |
| `/user/queue/calls` | server → one user | incoming call, accepted, ended, signalling |
| `/app/conversations/{id}/send` | client → server | send a message |
| `/app/conversations/{id}/typing` | client → server | typing indicator |
| `/app/calls/signal` | client → server | SDP offer/answer, ICE candidates |

Authentication happens once, on the STOMP `CONNECT` frame, in
`WebSocketAuthChannelInterceptor`. A frame without a valid token is dropped rather than
answered.

**REST is the source of truth; the socket is an accelerator.** Every live event has a REST
equivalent, so a client with a dead socket still works on refresh. That is why chat history
is fetched over HTTP and only appended to over the socket.

---

## Scaling seams

Nothing below is needed for a single node. Each is a one-file change when it is:

| Pressure | Change |
| --- | --- |
| More than one backend instance | `RateLimitService`: swap Caffeine for `bucket4j-redis`, same signature |
| More than one backend instance | `WebSocketConfig`: `enableStompBrokerRelay` instead of the simple broker |
| More than one backend instance | Schedulers need a lock — ShedLock is the usual answer |
| Media volume | Implement `StorageService` against S3; nothing above it knows what a storage key is |
| Candidate pool outgrows a few thousand | Precompute scores into a table rather than enlarging the in-memory ranking |
| Geo queries get slow | PostGIS and a GiST index, replacing the bounding-box pre-filter |
