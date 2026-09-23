# Backend

Spring Boot 3.3 on Java 21. PostgreSQL, Flyway, JWT, STOMP over WebSocket.

## Run it

```bash
docker compose up -d postgres      # from the repo root
// we can use local db too
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Flyway creates the schema and seeds interests, qualities, prompts and plans on first boot.
`ddl-auto` is `validate`, so a mismatch between an entity and a migration fails at startup
rather than quietly corrupting the schema.

```bash
mvn test                 # unit tests
mvn clean package        # jar in target/
```

## Layout

```
com.dating.platform
├── common/       response envelope, error codes, exception handling, base entities
├── config/       typed properties, security, WebSocket, OpenAPI, caching, async
├── security/     JWT, principal, STOMP authentication
├── ratelimit/    @RateLimit annotation and interceptor
├── util/         geo and date helpers
└── <feature>/    controller, service, repository, entity, dto per slice
```

Feature slices: `auth`, `user`, `profile`, `discovery`, `interaction`, `match`, `chat`,
`call`, `comment`, `standout`, `subscription`, `quota`, `media`, `notification`, `safety`.

Rules that hold everywhere:

- Controllers do HTTP shape and nothing else. No rules, no transactions.
- Services own transactions and business rules, and never know about HTTP.
- Entities never leave the service layer. Every response is a `dto` record.
- Every response goes through `ApiResponse<T>`; every error through
  `GlobalExceptionHandler`.

## Configuration

Everything tunable lives under the `app.*` keys in `application.properties` and binds to `AppProperties`.
No `@Value` strings scattered through the code.

| Key | What it controls |
| --- | --- |
| `app.jwt.*` | Secret, issuer, access and refresh TTL |
| `app.quota.*` | Daily and weekly allowances per tier |
| `app.matching.*` | Cron schedules, candidate pool size, compatibility threshold |
| `app.chat.opener-message-limit` | Messages allowed before a reply |
| `app.rate-limit.*` | Default bucket size |
| `app.storage.*` | Provider, root, size and type limits |

Environment overrides: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `FRONTEND_ORIGIN`,
`STORAGE_ROOT`, `SERVER_PORT`.

**`JWT_SECRET` must be set in production.** The default in `application.properties` is a development
placeholder, and the app refuses any secret shorter than 32 bytes.

## Scheduled jobs

| Job | Default schedule | What it does |
| --- | --- | --- |
| `AutoMatchScheduler.runWeekly` | Mondays 09:00 UTC | One match for every eligible user |
| `AutoMatchScheduler.runDaily` | Daily 09:00 UTC | One match for Premium users |
| `StandoutScheduler.refresh` | Every 6 hours, and on boot | Rebuilds the Standouts ranking |
| `SubscriptionService.expireLapsed` | Hourly | Safety net for missed renewal webhooks |
| `MediaAssetService.purgeOrphans` | Hourly | Deletes uploads never attached to a message |
| `CallService.expireStaleCalls` | Every minute | Ends calls nobody answered |
| `RefreshTokenCleanupJob` | Daily 03:30 UTC | Prunes long-expired refresh tokens |

On more than one instance these need a distributed lock (ShedLock). The unique constraints
make concurrent runs harmless, but they waste work.

## Tests

`mvn test` — 14 tests.

- `ApplicationContextTest` — loads the whole Spring context against in-memory H2. Cheap, and
  it catches a lot: every bean's dependencies resolve, no cycles, every
  `@ConfigurationProperties` binds, and **every JPQL query in every repository is parsed and
  validated at startup**, so a typo in an `@Query` fails here rather than the first time a
  user hits that endpoint. Native queries are the exception — they are only parsed when
  executed, so `DiscoveryRepository`'s PostgreSQL-specific SQL is not covered.
- `CompatibilityScorerTest` — symmetry, range, and that the weighting actually behaves
  (identical people beat opposites, a children mismatch drags the score down).
- `PopularityScorerTest` — that popularity is log-scaled and newcomers can surface.
- `ProfileCompletenessCalculatorTest` — the weighting that gates auto-match eligibility.

The test profile disables Flyway and lets Hibernate generate the schema. `V1` is
deliberately PostgreSQL-specific (pgcrypto, `TIMESTAMPTZ`, partial indexes, `NULLS LAST`),
and maintaining a second H2-flavoured migration set would drift from the real one. Schema
correctness is enforced against a real PostgreSQL instead, by `ddl-auto: validate` at
startup.

## Building on JDK 23+

The pom pins Lombok to 1.18.38. Spring Boot 3.3.5 manages 1.18.34, which cannot run on
JDK 23 or later — it reaches into `com.sun.tools.javac` internals that moved. The emitted
bytecode is still Java 21 either way; this only affects the machine doing the building.

## Further reading

- [`../docs/architecture.md`](../docs/architecture.md)
- [`../docs/api.md`](../docs/api.md)
- [`../docs/matching.md`](../docs/matching.md)
- [`../docs/security.md`](../docs/security.md)
