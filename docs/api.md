# API reference

Base URL `http://localhost:8080`. Interactive docs at `/swagger-ui.html`.

---

## The envelope

Every endpoint returns the same shape. Success:

```json
{
  "success": true,
  "message": "Profile saved",
  "data": { },
  "timestamp": "2026-09-19T10:15:30Z"
}
```

Failure:

```json
{
  "success": false,
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "You have used all 5 of your photo comments for now",
    "details": [{ "field": "bio", "message": "Bio cannot exceed 500 characters" }],
    "context": { "feature": "photo comments", "limit": 5, "resetsAt": "2026-09-20T00:00:00Z" }
  },
  "timestamp": "2026-09-19T10:15:30Z",
  "traceId": "4f2c..."
}
```

- `error.code` is the contract. Branch on it; never parse the message.
- `details` carries field-level validation failures, ready to map onto a form.
- `context` carries structured extras: the quota reset time, the tier a feature needs.
- `traceId` appears only for unexpected failures, so a user can quote it in support.

Paginated endpoints return `{ items, page, size, totalElements, totalPages, first, last }`.
Chat history returns `{ items, nextCursor, hasMore }` instead, because offset pagination
drifts as new messages arrive mid-scroll.

---

## Authentication

`Authorization: Bearer <accessToken>` on everything except the endpoints below.

Access tokens last 15 minutes. Refresh tokens are opaque, stored hashed, and rotate on every
use; presenting a revoked one revokes the whole family, because that pattern means the token
was stolen.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Public. 5/hour per IP. |
| POST | `/api/v1/auth/login` | Public. 10 per 5 min per IP. |
| POST | `/api/v1/auth/refresh` | Public. Rotates the token. |
| POST | `/api/v1/auth/logout` | Revokes this device's family. |
| POST | `/api/v1/auth/logout-all` | Revokes every session. |
| POST | `/api/v1/auth/change-password` | Revokes every other session. |

---

## Account and profile

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/users/me` | The caller's account |
| PATCH | `/api/v1/users/me/preferences` | Age, distance, gender, global mode, incognito |
| PUT | `/api/v1/users/me/location` | Coordinates. Never returned to anyone else. |
| POST | `/api/v1/users/me/complete-onboarding` | Requires a photo, a location, completeness 0.30+ |
| POST | `/api/v1/users/me/pause` | Hide the profile, keep the account |
| DELETE | `/api/v1/users/me` | Deactivate |
| POST | `/api/v1/users/me/heartbeat` | Drives the "active recently" badge |
| GET | `/api/v1/profile` | Own profile, including private fields |
| PATCH | `/api/v1/profile` | Partial update; omit a field to leave it, send empty to clear it |
| GET | `/api/v1/profiles/{userId}` | Another user's profile, redacted |
| GET/PUT/DELETE | `/api/v1/profile/prompts` | Prompt answers, max 3 |
| GET/POST/DELETE | `/api/v1/profile/photos` | Photos, max 9 |
| PUT | `/api/v1/profile/photos/order` | Reorder; the first becomes primary |
| GET | `/api/v1/reference/{interests,qualities,prompts}` | Public reference data, cached |

---

## Discovery, likes, matches

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/discovery/feed?page&size` | The home feed. POST because the filter is structured. |
| POST | `/api/v1/likes` | Consumes one daily like. Returns `matched: true` with the match. |
| POST | `/api/v1/likes/pass` | 30-day cooldown, not permanent |
| POST | `/api/v1/likes/rewind` | Requires `REWIND` |
| GET | `/api/v1/likes/inbound` | The Likes You tab. Redacted server side without `SEE_WHO_LIKES_YOU`. |
| GET | `/api/v1/matches` | Ordered by last interaction |
| DELETE | `/api/v1/matches/{id}` | Unmatch, optionally block |
| POST | `/api/v1/auto-match/run` | Weekly on free, daily on Premium |
| GET | `/api/v1/standouts?limit` | The curated shelf |

**Advanced feed filters** - `intents`, `minHeightCm`, `maxHeightCm`, `onlyVerified`,
`interestIds` - return `402 PREMIUM_REQUIRED` without `ADVANCED_FILTERS`. They are rejected
rather than silently ignored, so the client can show the upsell instead of wrong results.

---

## Chat and calls

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/conversations` | Includes `sendingState` |
| GET | `/api/v1/conversations/{id}/messages?before&limit` | Cursor paged, newest first |
| POST | `/api/v1/conversations/{id}/messages` | See the opener limit below |
| POST | `/api/v1/conversations/attachments` | Returns an asset id to reference when sending |
| POST | `/api/v1/conversations/{id}/read` | Clears unread, emits a read event |
| POST | `/api/v1/calls/conversations/{id}` | Starts a call, returns ICE servers |
| POST | `/api/v1/calls/{id}/accept` | Answer |
| POST | `/api/v1/calls/{id}/decline` | Reject |
| POST | `/api/v1/calls/{id}/hangup` | End |

### The opener limit

Until both people have spoken, whoever opened may send at most
`app.chat.opener-message-limit` (default 3) messages. After that:

```json
{ "success": false, "error": { "code": "OPENER_LIMIT_REACHED",
  "message": "You can send 3 messages before they reply" } }
```

`GET /conversations/{id}` reports this ahead of time so the UI never lets someone type a
paragraph and then rejects it:

```json
{ "sendingState": { "canSend": true, "remainingOpeners": 2, "openerLimit": 3 } }
```

### Attachments are two-step

Upload returns an **asset id**, and sending references that id. The client never passes a
storage key, because that would let anyone attach any object in the bucket to their own
message. Asset ids are single use and ownership-checked.

---

## Comments, plans, safety

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/photos/{photoId}/comments` | Consumes one of the daily allowance |
| GET | `/api/v1/photos/{photoId}/comments` | Paged |
| GET | `/api/v1/comments/quota` | Limit, used, remaining, reset time |
| DELETE | `/api/v1/comments/{id}` | Author or photo owner |
| GET | `/api/v1/plans` | Public |
| GET | `/api/v1/subscriptions/me/entitlements` | Every limit the UI shows comes from here |
| POST | `/api/v1/subscriptions` | Payments stubbed in this build |
| DELETE | `/api/v1/subscriptions/me` | Cancels renewal; access runs to period end |
| POST | `/api/v1/safety/blocks` | Symmetric in effect |
| POST | `/api/v1/safety/reports` | Optionally blocks too |

---

## WebSocket

Connect to `/ws` (SockJS) with `Authorization: Bearer <token>` on the STOMP `CONNECT` frame.

| Destination | Direction | Carries |
| --- | --- | --- |
| `/topic/conversations/{id}` | subscribe | messages, `TYPING`, `READ`, `DELETED` |
| `/user/queue/notifications` | subscribe | likes, matches, messages, comments |
| `/user/queue/calls` | subscribe | `INCOMING_CALL`, `CALL_ACCEPTED`, `CALL_ENDED`, `SIGNAL` |
| `/app/conversations/{id}/send` | send | a message |
| `/app/conversations/{id}/typing` | send | `{ "typing": true }` |
| `/app/calls/signal` | send | `{ callId, type, payload }` |

---

## Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | `details` has the fields |
| `UNAUTHORIZED`, `TOKEN_EXPIRED`, `TOKEN_INVALID` | 401 | Refresh, then retry once |
| `FORBIDDEN` | 403 | Not yours |
| `USER_BLOCKED` | 403 | One of you blocked the other |
| `OPENER_LIMIT_REACHED` | 403 | Wait for a reply |
| `NOT_FOUND` | 404 | |
| `ALREADY_LIKED`, `CONFLICT` | 409 | |
| `PROFILE_INCOMPLETE` | 428 | Finish onboarding |
| `QUOTA_EXCEEDED` | 402 | Allowance spent; `context` has the reset time |
| `PREMIUM_REQUIRED` | 402 | `context.requiredTier` says which plan |
| `RATE_LIMITED` | 429 | `Retry-After` header |
| `INTERNAL_ERROR` | 500 | `traceId` in the body and the logs |

---

## Rate limiting

Two separate systems, deliberately:

- **`ratelimit/`** - per IP or per user, in memory, token buckets. Abuse protection.
  Responses carry `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` on refusal.
- **`quota/`** - per user, persisted, resets daily or weekly. This is the product.

Every endpoint has a limit. Un-annotated endpoints fall back to 120 requests a minute.
