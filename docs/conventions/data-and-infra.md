# Data and infrastructure

## Postgres

- `snake_case`, plural table names, surrogate UUID primary keys, explicit foreign-key
  constraints (never enforced only in application code).
- A breaking schema change (drop/rename a column, incompatible type/nullability change)
  goes through expand-contract across at least two deploys, never a single destructive
  migration against a table already in use. An additive change (new nullable column, new
  table, new index) is a normal single migration.
- Connects to the shared local dev Postgres instance in development, in its own `inventory`
  database/role - never the shared `admin` superuser (see
  [security-practices](#security-practices) below). Locally:
  `CREATE ROLE inventory WITH LOGIN PASSWORD '...'; CREATE DATABASE inventory OWNER inventory;`
  run once against the shared instance.

## Flyway

- `spring.jpa.hibernate.ddl-auto` is always `validate` - Hibernate never creates or updates
  schema, in any environment including local dev. Every schema change is a hand-written,
  versioned SQL script.
- Scripts live in `api/src/main/resources/db/migration/` (the `api` module, since Flyway
  needs them on the classpath of whichever module actually boots), named
  `V{n}__{description}.sql`, strictly sequential, never edited once shipped - a later change
  is a new script.
- Environment-specific *data* (the bootstrap admin) is never a Flyway script - it would
  also run against a real environment. See `AdminBootstrapRunner`
  (`api/.../user/AdminBootstrapRunner.java`), an `ApplicationRunner` instead (see
  [docs/security.md](../security.md)).

## Redis

- Every cache name is a constant, never an inline string at the call site.
- Every cached entry has an explicit TTL - no un-expiring entries.
- Redis is a cache, not a system of record - every value it holds is rebuildable from
  Postgres. It also backs the two rate limiters (`RedisRateLimiter`) - see
  [docs/security.md](../security.md).

## Security practices

- Secrets via environment variables only (`POSTGRES_PASSWORD`, etc. - see
  `.env.example`), never committed, never baked into an image.
- Least privilege: the app connects as its own `inventory` Postgres role, never the shared
  `admin` superuser the local dev stack provisions for itself.
- Passwords are never stored or logged in plain text - BCrypt only (`BCryptPasswordHasher`).
  Authentication is never treated as authorization - every endpoint also checks the
  caller's role (`@RequireStaff`/`@RequireManager`/`@RequireAdmin`), not just *that* the
  caller is logged in. See [docs/security.md](../security.md).

## Rate limiting

- Two independent limiters, both built on the same `RedisRateLimiter` (fixed-window
  counter - `INCR` + `EXPIRE` on first hit) - see [docs/security.md](../security.md) for
  the full decision:
  - `LoginRateLimitFilter` - `POST /api/v1/auth/login` only, keyed by caller IP,
    configured via `app.rate-limit.login.{max-requests, window-seconds}`. Tight on
    purpose: the threat model is brute-forcing a password.
  - `ApiRateLimitFilter` - every authenticated business endpoint, keyed by the caller's
    own user id, configured via `app.rate-limit.api-wide.{max-requests, window-seconds}`.
    Generous by default (300 req/60s) - it exists to cap abuse, not to throttle normal use.
- Both run on Redis only - neither ever acquires a Postgres connection, so hitting the
  limit (or the limiter itself) adds zero pressure to the database connection pool.
- **Fails open.** If Redis is unreachable, requests are allowed rather than blocked - a
  rate limiter is a secondary defense; its own backing store being down must never take
  down the primary feature it protects. `spring.data.redis.timeout` is kept short (250ms)
  so that failure path is fast, not a multi-second hang per request.
- Any filter that must run only inside the Spring Security chain (not globally) needs its
  own `FilterRegistrationBean` with `.setEnabled(false)` in `SecurityConfig` - see the
  comment there for why (Spring Boot auto-registers every `@Component implements Filter` as
  a global container filter in addition to wherever Spring Security places it).

## CORS

- The mechanism is always wired into `SecurityConfig`'s filter chain
  (`CorsConfigurationSource` bean, `app.cors.allowed-origins`), but permits nothing
  cross-origin by default - "prepared for CORS," not "CORS open." Set
  `CORS_ALLOWED_ORIGINS` to a comma-separated list of real frontend origins when one exists.

## Local dev stack

Provided by a shared Docker Compose stack (Postgres 16, Redis 8) external to this repo -
this project connects via `localhost` and the published ports, and does not define its own
Postgres/Redis containers.

## Docker

- One `Dockerfile` at the repo root, multi-stage: a `maven:*-eclipse-temurin-25-alpine`
  build stage runs the **whole Maven reactor** (`api` depends on
  `infrastructure`/`application`/`domain` as local modules, not published artifacts - a
  single-module build can't resolve them), then only the `api` module's packaged jar is
  copied into a slim `eclipse-temurin:25-jre-alpine-*` runtime stage. Runtime container
  runs as a non-root user. Image tags pinned to an exact version, never a bare `latest`,
  per this project's own dependency-versioning convention applied to base images.
- Configuration (`POSTGRES_*`, `REDIS_*`, `JWT_*`, `APP_ADMIN_*`) is supplied via environment
  variables at `docker run`/compose time - never baked into the image.
- This repo defines **only** the `Dockerfile` for this one service - any
  `docker-compose.yml` orchestrating it alongside other services (a frontend, etc.) lives
  at the `inventory-system` monorepo root, not here.
- Connecting to the shared local dev stack (which runs on the host, not in this container's
  network) from inside the built image uses `host.docker.internal` for
  `POSTGRES_HOST`/`REDIS_HOST` - see README.md's Docker section for the
  full `docker run` example.
