# inventory-api

Inventory management API (Spring Boot, Clean Architecture, Postgres, Redis, self-issued JWT auth).

This file only points to where the real conventions live - it is never edited to restate
them. If a rule changes, edit the relevant file under `docs/`, not this one.

## Read in this order for any task

1. [docs/conventions/engineering.md](docs/conventions/engineering.md) - naming, no magic
   values, DRY/SOLID, comments, definition of done, scope discipline, ask-vs-assume.
2. [docs/conventions/architecture.md](docs/conventions/architecture.md) - the four-module
   Clean Architecture layout and dependency direction.
3. [docs/conventions/api-design.md](docs/conventions/api-design.md) - REST conventions,
   versioning, pagination envelope, OpenAPI.
4. [docs/conventions/error-handling-and-logging.md](docs/conventions/error-handling-and-logging.md)
5. [docs/conventions/testing.md](docs/conventions/testing.md)
6. [docs/conventions/data-and-infra.md](docs/conventions/data-and-infra.md) - Postgres,
   Flyway, Redis, Docker, security practices.
7. [docs/conventions/git.md](docs/conventions/git.md)
8. [docs/security.md](docs/security.md) - the authentication/authorization decision (this
   application is its own identity provider; no external auth server, no multitenancy) and
   how it is implemented end to end. Read this before touching anything under
   `api/.../security` or `api/.../user`.

## Spec Driven Development

Every new feature is specified under `docs/specs/<nn>-<feature>/` (a `requirements.md` plus
a hand-written `openapi.yaml`) and reviewed **before** any controller/entity/migration is
written. [docs/specs/00-domain-overview.md](docs/specs/00-domain-overview.md) is the
standing, whole-domain reference; each numbered feature folder is one implementable
vertical slice of it. The same process applies to the next feature this domain doesn't
cover yet, whenever one comes up - write the spec, get it reviewed, implement only after
approval.

## Project structure

```
inventory-api/
  pom.xml            # parent, packaging=pom, declares the modules below
  domain/             # business entities, value objects, repository interfaces
  application/       # use cases, application-owned contracts (PageResponse, etc.)
  infrastructure/     # Spring Data JPA, Redis, Flyway
  api/                 # the only packaged/runnable module: controllers, security, config
  docs/
```

Implemented so far:
- [docs/specs/01-catalog-and-stock/](docs/specs/01-catalog-and-stock/requirements.md) -
  categories, products, warehouses, read-only stock levels.
- [docs/specs/02-stock-movements/](docs/specs/02-stock-movements/requirements.md) -
  receiving, shipping, transfers, adjustments; the only writer of `StockLevel.quantityOnHand`.
- [docs/specs/03-suppliers-and-purchase-orders/](docs/specs/03-suppliers-and-purchase-orders/requirements.md) -
  suppliers, the purchase order state machine, and receiving (calls into `02-stock-movements`).
- [docs/specs/04-low-stock-alerts/](docs/specs/04-low-stock-alerts/requirements.md) -
  reorder thresholds and the read-only `/stock-alerts` projection.
- [docs/specs/06-roles-and-permissions/](docs/specs/06-roles-and-permissions/requirements.md) -
  platform-level, cross-cutting: `STAFF`/`MANAGER`/`ADMIN`, applied retroactively to every
  endpoint in `01`-`04`. Every feature from here on is written against it, not retrofitted.
- [docs/specs/07-audit-trail/](docs/specs/07-audit-trail/requirements.md) - platform-level,
  cross-cutting: a `CREATED`/`UPDATED` entry per write, `GET /api/v1/audit*` (`ADMIN` only).
- [docs/specs/08-sales-orders-and-customers/](docs/specs/08-sales-orders-and-customers/requirements.md) -
  customers, the sales order state machine (the outbound twin of `03`), and the stock
  reservation mechanism - the first writer of `StockLevel.quantityReserved`.
- [docs/specs/09-cycle-counts/](docs/specs/09-cycle-counts/requirements.md) - physical stock
  count sessions; approval posts adjustments through `02-stock-movements`' own recording
  service.
- [docs/specs/10-reporting/](docs/specs/10-reporting/requirements.md) - inventory valuation,
  a stock-movement summary, and stock aging; three read-only computed reports, no new
  persisted entity.
- [docs/specs/11-user-management/](docs/specs/11-user-management/requirements.md) -
  platform-level: this application is its own identity provider (see `docs/security.md`);
  ADMIN creates/manages teammates locally, no email delivery. `AdminBootstrapRunner`
  bootstraps the one initial ADMIN at startup.
- [docs/specs/12-catalog-bulk-import-export/](docs/specs/12-catalog-bulk-import-export/requirements.md) -
  CSV bulk import/export for `Product` only, row-partial-success.
- [docs/specs/14-webhooks/](docs/specs/14-webhooks/requirements.md) - platform-level:
  transactional outbox + scheduled dispatcher for four event types, never a synchronous HTTP
  call from a business request.

The whole domain in [docs/specs/00-domain-overview.md](docs/specs/00-domain-overview.md) is
implemented as of `10-reporting`, including the sales-orders/cycle-counts extensions `08`/`09`
added to it; `11`/`12`/`14` are platform-level SaaS-readiness features layered on top, not
part of that domain overview. The next feature (whatever it turns out to be) still follows
the same spec-first process above.

A `Dockerfile` for this service exists at the repo root - see
[docs/conventions/data-and-infra.md](docs/conventions/data-and-infra.md)'s Docker section.

Rate limiting (one limiter on `POST /api/v1/auth/login` keyed by caller IP, a second one
api-wide keyed by caller user id) and CORS (wired, zero origins allowed by default) are in
place - see [docs/security.md](docs/security.md) and that same conventions file's CORS
section. No CI/CD yet - out of scope until requested.
