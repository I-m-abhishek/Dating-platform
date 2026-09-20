# Security and privacy

---

## Authentication

**Access tokens** — stateless JWT, HS256, 15 minutes. Carry the subject, email and roles.
`JwtAuthenticationFilter` never touches the database; everything the request needs is in the
token. The trade-off is that a revoked account keeps working for at most 15 minutes, which is
what the short TTL buys back.

**Refresh tokens** — opaque, 48 random bytes, stored as a SHA-256 hash. SHA-256 rather than
BCrypt is correct here: the input is already high entropy, so a slow KDF buys nothing and
costs latency on every refresh.

**Rotation with family revocation.** Every refresh issues a new token and revokes the old
one. All tokens descended from one login share a `family_id`. Presenting an already-revoked
token means it was stolen and replayed, so the entire family is revoked — which logs out the
attacker and the legitimate user, who can simply sign in again.

**Password rules.** BCrypt, cost 12. Passwords are 8–72 characters (72 because BCrypt
silently truncates beyond that, and a silently truncated password is a real vulnerability)
with an upper case letter, a lower case letter and a digit.

**Account enumeration.** Login returns `INVALID_CREDENTIALS` for both "no such account" and
"wrong password". Registration is the one endpoint that has to distinguish them, which is why
it is rate limited to 5 per hour per IP.

---

## Authorisation

Three layers, each doing one job:

1. **Route level** — `SecurityConfig`. Public endpoints are enumerated explicitly; everything
   else requires authentication. `/api/v1/admin/**` requires `ROLE_ADMIN`.
2. **Resource level** — services check ownership. `requireParticipant` on matches,
   conversations and calls; `findByIdAndUserId` on photos and prompts. A valid token for user
   A never reaches user B's data.
3. **Feature level** — `EntitlementService.require`. Paid features are refused at the service,
   not hidden in the UI.

---

## The paywall is real

Worth stating plainly, because it is the most common place this goes wrong.

For an account without `SEE_WHO_LIKES_YOU`, `LikeService.blurredRow()` constructs a
placeholder with no user id, no display name, no photo URL and no note. The redaction happens
before serialisation. The blur in the browser is a visual treatment over an empty object, and
there is nothing to recover from the network tab.

---

## Rate limiting

`ratelimit/` — token buckets (Bucket4j over Caffeine), per user or per IP.

| Endpoint | Limit |
| --- | --- |
| `auth.register` | 5 / hour / IP |
| `auth.login` | 10 / 5 min / IP |
| `auth.change-password` | 5 / hour |
| `like.send` | 60 / min |
| `chat.send` | 120 / min |
| `comment.create` | 30 / min |
| `photo.upload` | 30 / hour |
| `call.start` | 20 / hour |
| `automatch.run` | 10 / hour |
| anything else | 120 / min |

Pre-auth endpoints are scoped by IP because there is no user yet. `X-Forwarded-For` is
honoured for a single proxy hop — **the reverse proxy must strip client-supplied values**, or
the limit can be evaded by forging the header.

Refusals return `429` with `Retry-After` and `X-RateLimit-*`.

---

## Uploads

- **Type is verified twice.** The declared `Content-Type` is checked against an allow-list,
  then the first bytes are checked against the real magic numbers for JPEG, PNG, GIF, WebP and
  HEIC. `Content-Type` is client supplied and trivially forged.
- **File names are never used as paths.** Every object is stored as a UUID plus an extension
  derived from the verified type. Path traversal and content-sniffing tricks both disappear.
- **Root containment.** Every resolved path is checked to be inside the storage root before
  any read or delete.
- **Size limits** — 10 MB per image, 25 MB per attachment, enforced before anything touches
  storage.
- **Two-step attachment flow.** Uploading returns an asset id; sending references the id. The
  client never passes a storage key, so nobody can attach an object they do not own. Asset
  ids are single use and ownership-checked, and unconsumed uploads are swept after 24 hours.

---

## Privacy

**Location.** Exact coordinates are stored for distance maths and never leave the server.
Every response carries a rounded distance in kilometres. `GeoUtils.fuzz` exists for snapping
coordinates to a coarse grid if a deployment wants trilateration resistance as well.

**Last seen.** Exposed only as a boolean "active recently" (72 hours), and the exact timestamp
only when that boolean is already true. Precise last-seen data is a stalking vector.

**Blocking is symmetric.** Stored one-directionally, enforced both ways. Every query that can
surface a person — discovery, likes, standouts, comments, chat, calls — consults
`BlockService` first.

**Calls never touch the server.** Only SDP and ICE candidates are relayed, after verifying
the sender is in that call. The payload is never parsed. Audio and video go peer to peer.

**Deletion is soft where it has to be.** Messages and comments are tombstoned rather than
removed: the other person has already seen them, and moderation needs the original text. A
silently vanishing history is its own trust problem.

---

## Error handling

`GlobalExceptionHandler` is the only place that maps an exception to a status code.

- Expected failures log at WARN without a stack trace.
- Unexpected failures log at ERROR with a generated `traceId` that is also returned, so a
  user can quote it in a support ticket.
- SQL, class names and stack traces never reach a response body.
  `server.error.include-stacktrace: never` and `include-message: never` back this up at the
  container level.

---

## Headers

Set by Spring Security on the API and by `next.config.mjs` on the documents:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Strict-Transport-Security` with a one-year max-age and subdomains
- `Permissions-Policy: camera=(self), microphone=(self), geolocation=(self)`

CORS is an explicit origin allow-list from `app.cors.allowed-origins`. No wildcard, because
credentials are allowed.

---

## Before production

- [ ] Set `JWT_SECRET` to at least 32 random bytes. The default in `application.yml` is a
      development placeholder and the app says so.
- [ ] Set `FRONTEND_ORIGIN`; the prod profile uses it for CORS.
- [ ] Terminate TLS in front of the API and let the proxy strip `X-Forwarded-For`.
- [ ] Move storage to S3 by implementing `StorageService`.
- [ ] Add a distributed lock (ShedLock) before running more than one instance, or the
      schedulers will run in parallel. The unique constraints make that harmless but wasteful.
- [ ] Wire real payment verification — see [`monetisation.md`](monetisation.md).
- [ ] Add photo moderation behind `Photo.moderationStatus`, which already exists.
- [ ] Ship logs somewhere, and alert on the `traceId` ERROR path.
