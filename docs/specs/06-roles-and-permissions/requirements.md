# Feature spec: Roles & permissions (RBAC)

Status: **implemented and tested** (`Role`, `RoleJwtAuthenticationConverter`, the
`@RequireStaff`/`@RequireManager`/`@RequireAdmin` composed annotations, all under
`api/.../security`/`domain/.../user`; retrofitted onto every controller in `01`-`04` plus
every platform-level endpoint added afterward).

Scope: a platform-level, cross-cutting concern - not a business-domain vertical slice and
therefore not referenced from [00-domain-overview.md](../00-domain-overview.md). It changes
**authorization only** - every endpoint's existing behavior (validation, status codes) is
unchanged, it just gains one more precondition: the caller's role. Applies retroactively to
every endpoint shipped in [01-catalog-and-stock](../01-catalog-and-stock/requirements.md),
[02-stock-movements](../02-stock-movements/requirements.md),
[03-suppliers-and-purchase-orders](../03-suppliers-and-purchase-orders/requirements.md), and
[04-low-stock-alerts](../04-low-stock-alerts/requirements.md), and is a precondition every
feature numbered 07 onward is written against from day one.

## Decision

- **Three roles, strictly additive**: `STAFF` < `MANAGER` < `ADMIN`. A higher role can do
  everything a lower one can, plus more - there is no role that can do something a higher role
  cannot.
- **Roles are a plain column on this application's own `users` table** (see
  [docs/security.md](../../security.md) and
  [11-user-management](../11-user-management/requirements.md)) - no per-user permission
  configuration, no separate roles table; the three roles are fixed and global to the
  application, exactly like the fixed `StockMovementType` enum.
- **Carried in the access token's `role` claim** - a single string, never a list (a user
  holds exactly one role at a time), set by `JwtAccessTokenIssuer` at login and read back by
  `RoleJwtAuthenticationConverter`.
- A token with a missing or unrecognized `role` claim value authenticates successfully but
  is authorized for **nothing** business-related - every endpoint below requires at least
  `STAFF`.
- Authorization is enforced declaratively with `@PreAuthorize`-backed composed annotations
  (`@RequireStaff`/`@RequireManager`/`@RequireAdmin`) at the controller method - the layer that
  already owns Spring Security per
  [architecture.md](../../conventions/architecture.md) - never in `application`/`domain`.
  One endpoint (`POST /api/v1/stock-movements`) additionally branches on request-body content (see
  below), since a single endpoint accepts movement types of different sensitivity.

## Permission matrix

| Capability | STAFF | MANAGER | ADMIN |
|---|---|---|---|
| Read any resource (`GET`) | yes | yes | yes |
| Create/edit catalog master data (categories, products, warehouses) | no | yes | yes |
| Create/edit suppliers | no | yes | yes |
| Record a movement that is part of an existing flow (`RECEIPT`/`SHIPMENT` via `POST /stock-movements`, a transfer, receiving a purchase-order line) | yes | yes | yes |
| Create/place/cancel/edit a purchase order | no | yes | yes |
| Define reorder thresholds | no | yes | yes |
| A bare `ADJUSTMENT_INCREASE`/`ADJUSTMENT_DECREASE` via `POST /stock-movements` (a correction with no order/count behind it) | no | no | yes |
| Manage users (`/api/v1/users*`) | no | no | yes |
| Manage webhook subscriptions | no | no | yes |

## Endpoint-by-endpoint mapping (retroactive)

| Endpoint | Required role |
|---|---|
| `GET`/`POST`/`PATCH /api/v1/categories*` | STAFF (read) / MANAGER (write) |
| `GET`/`POST`/`PATCH /api/v1/products*` | STAFF (read) / MANAGER (write) |
| `GET`/`POST`/`PATCH /api/v1/warehouses*` | STAFF (read) / MANAGER (write) |
| `GET /api/v1/stock-levels*` | STAFF |
| `GET /api/v1/stock-movements*` | STAFF |
| `POST /api/v1/stock-movements` | STAFF, **except** `ADJUSTMENT_INCREASE`/`ADJUSTMENT_DECREASE`, which require ADMIN - checked in the controller against the request body's `type`, not declaratively, since `@PreAuthorize` cannot branch on body content without relying on compiler debug metadata this project does not otherwise depend on |
| `POST /api/v1/stock-movements/transfers` | STAFF |
| `GET /api/v1/suppliers*` | STAFF (read) / MANAGER (write) |
| `GET /api/v1/purchase-orders*` | STAFF |
| `POST`/`PATCH /api/v1/purchase-orders*`, `.../placement`, `.../cancellation` | MANAGER |
| `POST /api/v1/purchase-orders/{id}/lines/{lineId}/receipts` | STAFF (receiving is executing an already-placed order, not deciding to place one) |
| `GET`/`POST`/`PATCH /api/v1/reorder-thresholds*` | STAFF (read) / MANAGER (write) |
| `GET /api/v1/stock-alerts` | STAFF |
| `/api/v1/users*` | ADMIN |

## Acceptance criteria

1. A request with a valid access token but no recognized value in the `role` claim gets
   `403` with code `FORBIDDEN` on every business endpoint in the mapping above.
2. A `STAFF` token calling a MANAGER-or-above endpoint (e.g. `POST /api/v1/products`) gets `403`,
   `FORBIDDEN`.
3. A `MANAGER` token calling `POST /api/v1/stock-movements` with
   `"type": "ADJUSTMENT_DECREASE"` gets `403`, `FORBIDDEN` - `MANAGER` is not enough for a bare
   adjustment.
4. An `ADMIN` token can do everything a `MANAGER` or `STAFF` token can, with no endpoint that
   rejects `ADMIN` while accepting a lower role.
5. A missing `role` claim entirely (a token that is otherwise perfectly valid) converts to
   zero authorities, not an error - the request still authenticates, then gets `403` from the
   first `@PreAuthorize` check, exactly like criterion 1.
6. Every existing acceptance criterion from `01`-`04`'s own specs still holds unchanged once a
   caller has the right role - this feature adds a precondition, it does not change any status
   code, error code, or response shape those specs already define.

## Out of scope for this feature

- A database-backed, per-deployment-configurable permission model - see "Decision" above
  for why three fixed, global roles are deliberate, not a placeholder.
- Resource-level/ownership-based authorization (e.g. "only the manager who created this purchase
  order can cancel it") - out of scope; authorization here is role-based only, not row-owner-based.
