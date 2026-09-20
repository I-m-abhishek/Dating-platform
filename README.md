# Two & Two — a dating platform

A full dating product: profiles, discovery, matching, chat with voice calls, photo comments,
a guaranteed weekly auto-match, and a paid tier that lifts the limits.

- **Backend** — Java 21 / Spring Boot 3.3, PostgreSQL, Flyway, JWT, STOMP over WebSocket.
- **Frontend** — Next.js 15 (App Router), TypeScript, Tailwind, TanStack Query, Zustand.

---

## What is here

| Path | What it is |
| --- | --- |
| `backend/` | The API. Feature-sliced Spring Boot service. |
| `frontend/` | The web client. Next.js App Router. |
| `docs/` | Architecture, API reference, and the design notes behind the algorithms. |
| `docker-compose.yml` | PostgreSQL (and an optional Redis) for local development. |

---

## Running it

### 1. Database

```bash
docker compose up -d postgres
```

### 2. Backend

Requires JDK 21+ and Maven 3.9+. Verified on JDK 24 / Maven 3.9.9.

```bash
cd backend && mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Flyway creates the schema and seeds interests, qualities, prompts and plans on first boot.

### 3. Frontend

Requires Node 20+.

```bash
cd frontend && npm install && npm run dev
```

- App: http://localhost:3000
- Copy `.env.example` to `.env.local` if your API is not on `localhost:8080`.

---

## The features, and where they live

| Feature | Backend | Frontend |
| --- | --- | --- |
| Profiles, photos, prompts, interests, qualities | `profile/` | `app/(main)/profile`, `components/profile` |
| Home feed with distance and age filters | `discovery/` | `app/(main)/home`, `components/discovery` |
| Likes, passes, rewind | `interaction/` | `lib/hooks/useFeed.ts` |
| Likes You, blurred until you pay | `interaction/service/LikeService` | `app/(main)/likes` |
| Matches and unmatching | `match/` | `app/(main)/matches` |
| **Weekly / daily auto-match** | `match/service/AutoMatchService` | `lib/hooks/useMatches.ts` |
| **Standouts** | `standout/` | `app/(main)/standouts` |
| Chat, attachments, read receipts, typing | `chat/` | `app/(main)/messages`, `components/chat` |
| **Opener message limit** | `chat/service/ChatService` | `components/chat/MessageComposer` |
| Voice and video calls (WebRTC) | `call/` | `lib/hooks/useCall.ts`, `components/chat/CallOverlay` |
| **Photo comments, 5 a day free** | `comment/`, `quota/` | `components/profile/PhotoCommentSheet` |
| Plans, entitlements, paywalls | `subscription/` | `app/(main)/plans`, `components/paywall` |
| Blocking, reporting | `safety/` | `app/(main)/u/[userId]` |
| Rate limiting | `ratelimit/` | — |

---

## The decisions worth knowing about

**Quotas are not rate limits.** They look similar and are deliberately separate systems.
`ratelimit/` is per-IP, in memory, and exists to stop abuse. `quota/` is per-user, persisted,
and is the product — "five comments a day" is a feature with a price attached, and it has to
survive a restart and a load balancer.

**The paywall is enforced in the response, not the UI.** The Likes You grid does not send a
blurred image; for a free account it sends no name and no photo URL at all. A paywall that
can be lifted with dev tools is decoration.

**Matches are stored with a canonical ordering.** `user_a_id` is always the smaller UUID, so
one unique constraint enforces "one match per pair" even when both people like each other at
the same instant.

**Auto-match is idempotent by construction.** One `auto_match_runs` row per (user, cadence,
period), with a unique constraint. A scheduler retry, a manual trigger and a redeploy
mid-run all converge on the same answer instead of handing somebody two matches.

**No match is better than a bad match.** When the auto-match engine finds nobody above the
compatibility threshold it records `NO_CANDIDATE` and says so, rather than lowering the bar.

**Calls never touch the server.** Only SDP and ICE are relayed; audio and video go peer to
peer. Cheaper, lower latency, and far less of a privacy surface.

See [`docs/architecture.md`](docs/architecture.md) for the full picture and
[`docs/matching.md`](docs/matching.md) for how compatibility is actually computed.

---

## Documentation

| Document | Covers |
| --- | --- |
| [`docs/architecture.md`](docs/architecture.md) | Layering, module boundaries, request lifecycle, data model |
| [`docs/api.md`](docs/api.md) | Every endpoint, the response envelope, error codes |
| [`docs/matching.md`](docs/matching.md) | Compatibility scoring, auto-match, standouts ranking |
| [`docs/monetisation.md`](docs/monetisation.md) | Tiers, features, quotas, how to change a limit |
| [`docs/frontend.md`](docs/frontend.md) | Next.js structure, the HOC layer, state management |
| [`docs/security.md`](docs/security.md) | Auth, token rotation, rate limiting, privacy posture |

---

## Build status

Both sides build clean:

| | Result |
| --- | --- |
| `mvn test` | 14 tests, 0 failures — including a full Spring context load |
| `mvn package` | `dating-platform-1.0.0.jar` |
| `npm run typecheck` | clean, under `strict` + `noUncheckedIndexedAccess` |
| `npm run build` | 16 routes, ESLint clean |

The backend has not been run against a real PostgreSQL here (no Docker or Postgres on the
build machine), so Flyway's migrations and the four native queries in `DiscoveryRepository`
are unexecuted. Everything else — bean wiring, entity mapping, and every JPQL query — is
verified by the context test.

## Status

Everything listed above is implemented. Two things are deliberately stubbed and marked as
such in the code:

- **Payments.** `SubscriptionService` grants a plan immediately and records the token
  verbatim. Wiring Stripe or the app stores means adding a webhook that flips
  `Subscription.status`; nothing downstream reads payment state directly.
- **Photo moderation.** `Photo.moderationStatus` exists and defaults to `APPROVED`. The
  hook for a real moderation pipeline is there; the pipeline is not.
