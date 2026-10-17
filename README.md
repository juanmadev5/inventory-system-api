# inventory-api

Inventory management API.

## Stack

- **Backend**: Spring Boot 4.1, Java 25, Maven multi-module.
- **Database**: PostgreSQL, Flyway-migrated.
- **Cache**: Redis.
- **Auth**: this application is its own identity provider - self-issued/validated JWT, no
  external auth server. See [docs/security.md](docs/security.md).

See [AGENTS.md](AGENTS.md) and [docs/](docs/) for the full architecture and conventions.

## Prerequisites

- JDK 25
- Maven (or use the bundled `./mvnw` / `mvnw.cmd`)
- The shared [`local-infrastructure`](../../local-infrastructure) Docker Compose stack
  running (PostgreSQL, Redis) - this project does not bring up its own.

## Getting started

1. Start `local-infrastructure` (see that repo's own README).
2. One-time: create this project's own database and role in the shared Postgres instance
   (never reuse the shared `admin` superuser):
   ```sql
   CREATE ROLE inventory WITH LOGIN PASSWORD '<pick a local password>';
   CREATE DATABASE inventory OWNER inventory;
   ```
3. Copy [`.env.example`](.env.example) to `.env` and fill in the password from step 2. The
   `dev` profile (`application-dev.yml`) already ships safe defaults for everything else
   (`JWT_SECRET`, `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`) - override them in `.env` only if
   you want different local values. **Any non-`dev` run (prod, staging, Docker, CI) must
   set `JWT_SECRET` explicitly** (see the [Docker](#docker) example below) and
   [docs/security.md](docs/security.md) for why.
4. This project reads configuration from environment variables, not from `.env` directly -
   load it into your shell before running anything below:
   ```sh
   set -a; source .env; set +a
   ```
5. Run the app (the `api` module is the only runnable one):
   ```sh
   ./mvnw spring-boot:run -pl api -Dspring-boot.run.profiles=dev
   ```
   On first boot, with an empty `users` table, `AdminBootstrapRunner` creates one `ADMIN`
   from `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` - see
   [docs/security.md](docs/security.md). Log in as that user:
   ```sh
   curl -X POST localhost:8081/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@example.com","password":"ChangeMe123!"}'
   ```
   to get a bearer token; from there, that admin creates everyone else through
   `POST /api/v1/users` (see
   [docs/specs/11-user-management/](docs/specs/11-user-management/requirements.md)). Every
   business endpoint requires one of three roles - `STAFF`, `MANAGER`, or `ADMIN` - in the
   token's `role` claim (see
   [docs/specs/06-roles-and-permissions/](docs/specs/06-roles-and-permissions/requirements.md)).
6. Swagger UI: <http://localhost:8081/swagger-ui.html>. Health check:
   <http://localhost:8081/actuator/health>.

## Docker

`Dockerfile` (multi-stage: builds the whole Maven reactor, ships only the `api` module's
jar in a slim runtime image, non-root user) is defined in this repo for the `inventory-api`
service itself:

```sh
docker build -t inventory-api .
docker run --rm -p 8081:8081 \
  -e POSTGRES_HOST=host.docker.internal -e POSTGRES_PASSWORD=<your password> \
  -e REDIS_HOST=host.docker.internal \
  -e JWT_SECRET=<a long random secret> \
  -e APP_ADMIN_EMAIL=admin@example.com -e APP_ADMIN_PASSWORD=<a strong password> \
  inventory-api
```

Any `docker-compose.yml` orchestrating this service alongside others (a frontend, etc.)
lives at the `inventory-system` monorepo root, not in this repo - per
[docs/conventions/data-and-infra.md](docs/conventions/data-and-infra.md), each service
only ever defines its own `Dockerfile`.

## Environment variables / configuration

See [`.env.example`](.env.example) for every variable the app reads and its default.
Real values live only in your own local shell/`.env` (gitignored, never committed).

## Available commands

From the repo root (acts on all four modules: `domain`, `application`, `infrastructure`,
`api`):

- `./mvnw compile` - compile everything.
- `./mvnw checkstyle:check` - static analysis (mandatory before any change is "done" - see
  [docs/conventions/engineering.md](docs/conventions/engineering.md)).
- `./mvnw test -pl api` - run every test, including the Testcontainers-backed integration
  tests (need Docker running - they bring up their own Postgres, no `local-infrastructure`
  dependency) and the `dev`-profile smoke test (`InventoryApiApplicationTests` - this one
  *does* need `local-infrastructure`'s Postgres/Redis running, with the `inventory`
  role/database from step 2 above).
- `./mvnw install -DskipTests` - install all modules to the local repo (needed once after
  cloning, or after changing a module's `pom.xml`, so sibling-module dependencies resolve).
- `./mvnw spring-boot:run -pl api -Dspring-boot.run.profiles=dev` - run the app.

## Project structure

```
inventory-api/
  domain/          # business entities, value objects, repository interfaces
  application/   # use cases, application-owned contracts - no Spring/JPA dependency
  infrastructure/ # Spring Data JPA, Redis, Flyway
  api/             # the only packaged/runnable module
  docs/            # architecture decisions, conventions, Spec Driven Development specs
```

See [AGENTS.md](AGENTS.md) for the full read-this-first list.
