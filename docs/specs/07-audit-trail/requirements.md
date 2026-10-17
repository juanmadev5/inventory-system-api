# Feature spec: Audit trail

Status: **implemented and tested** (`AuditLogEntry`/`AuditLogRepository` in `domain`,
`AuditLogService`/`CurrentActor` in `application`, `AuditLogJpaEntity` (jsonb snapshots via
Hibernate's native `@JdbcTypeCode(SqlTypes.JSON)`) in `infrastructure`, `AuditLogController`/
`JwtCurrentActor` in `api`; wired into `CategoryService`, `ProductService`, `WarehouseService`,
`SupplierService`, `PurchaseOrderService`, `ReorderThresholdService`). `V6__audit_log.sql`.
96 tests across the `api` module, all green, including
`AuditLogControllerIntegrationTest` (7 tests: role gating, created/updated snapshots, no entry
on a rejected write, get-by-id).

Scope: a platform-level, cross-cutting concern - like
[06-roles-and-permissions](../06-roles-and-permissions/requirements.md), not a business-domain
vertical slice, so not referenced from [00-domain-overview.md](../00-domain-overview.md). Builds on
[06-roles-and-permissions](../06-roles-and-permissions/requirements.md): the one new endpoint this
feature adds (`GET /api/v1/audit`) requires `ADMIN`. Applies retroactively to every mutating
endpoint shipped in [01-catalog-and-stock](../01-catalog-and-stock/requirements.md),
[03-suppliers-and-purchase-orders](../03-suppliers-and-purchase-orders/requirements.md), and
[04-low-stock-alerts](../04-low-stock-alerts/requirements.md).

## Decision

- **What gets audited**: every create/update of an entity that is not already its own
  immutable ledger. Concretely: `Category`, `Product`, `Warehouse`, `Supplier`, `PurchaseOrder`
  (including every state transition - placement, cancellation, receiving a line - each recorded
  as an `UPDATED` entry showing the before/after status), `ReorderThreshold`.
- **What does not get audited, and why**: `StockMovement` - per
  [00-domain-overview.md](../00-domain-overview.md), it is already an append-only ledger that
  *is* its own audit trail; adding a second one would duplicate it. `StockLevel` - a derived
  projection, never written to directly. `StockAlert` - computed, not a resource anyone writes.
  Any future feature that is itself an immutable ledger follows the same reasoning; anything else
  added later (sales orders, cycle counts, customers) is audited the same way as the entities
  above.
- **Shape**: one `AuditLogEntry` per write - `id`, `entityType` (closed enum:
  `CATEGORY`/`PRODUCT`/`WAREHOUSE`/`SUPPLIER`/`PURCHASE_ORDER`/`REORDER_THRESHOLD`, extended
  whenever a future feature adds another audited entity, same convention as
  `StockMovementType`), `entityId`, `action` (closed enum: `CREATED`/`UPDATED` - deliberately not
  one action per state transition; the before/after snapshots already show what changed, so the
  action set stays small and stable), `actorId` (the JWT's `sub` claim; nullable - see below),
  `actorUsername` (the JWT's `email` claim if present, else `null`), `occurredAt`,
  `oldValue` (a snapshot of the entity before the change; `null` for `CREATED`), `newValue` (a
  snapshot after the change; always present).
- **Snapshots are generic, not hand-built per entity**: the application layer converts whatever
  domain object it already has (the plain Java object returned by `save(...)`) into a
  `Map<String, Object>` via reflection (`ObjectMapper.convertValue`), rather than each service
  hand-listing its entity's fields - that would duplicate the entity's own field list, a DRY
  violation per [engineering.md](../../conventions/engineering.md) the moment a field is added to
  one but not the other. Stored as Postgres `jsonb` (Hibernate's native JSON mapping for a
  `Map<String, Object>` field, no extra library).
- **`actorId` can be `null`**: only when a write happens with no authenticated caller in context -
  in production this cannot happen (every mutating endpoint requires at least `STAFF`, per
  [06-roles-and-permissions](../06-roles-and-permissions/requirements.md)), but a handful of
  tests (e.g. the stock-movement concurrency tests) deliberately call an application service
  directly, bypassing the JWT layer on purpose - the audit write must not fail in that case,
  it just can't attribute an actor.
- **Recording happens inside the same transaction as the business write it documents** - no
  separate async/eventual-consistency path. If recording an audit entry fails, the business write
  it would have documented rolls back with it; a committed change with no audit trail is worse
  than a failed request.
- **Audit entries are themselves immutable** - no update or delete endpoint, ever, same as
  `StockMovement`.

## Endpoint

- `GET /api/v1/audit` - paginated, filterable by `entityType`, `entityId`, `action`,
  `occurredFrom`, `occurredTo`. Requires `ADMIN`.
- `GET /api/v1/audit/{id}` - single entry by id. Requires `ADMIN`.

## Acceptance criteria

1. Creating a `Category`/`Product`/`Warehouse`/`Supplier`/`PurchaseOrder`/`ReorderThreshold`
   produces exactly one `AuditLogEntry` with `action = CREATED`, `oldValue = null`, and
   `newValue` matching the created entity's fields.
2. Updating any of the above (including a purchase order's placement, cancellation, or receiving
   a line) produces exactly one `AuditLogEntry` with `action = UPDATED`, `oldValue` matching the
   entity's fields immediately before the change, and `newValue` matching it immediately after.
3. `actorId`/`actorUsername` on a real HTTP-originated write match the authenticated caller's
   JWT `sub`/`email` claims.
4. A request to either audit endpoint from a caller with less than `ADMIN` gets `403`,
   `FORBIDDEN`.
5. A write that fails its own business validation (e.g. a duplicate category name) produces
   **no** audit entry - only a successfully persisted change is recorded.

## Out of scope for this feature

- Auditing `StockMovement`, `StockLevel`, or `StockAlert` - see "Decision" above.
- Field-level diffing/highlighting in the response - the client compares `oldValue`/`newValue`
  itself; this feature returns both snapshots, not a computed diff.
- Any retention/archival policy for audit entries - they accumulate indefinitely for now, same as
  every other table in this project; revisit only if a real need appears.
- Exposing audit entries through any endpoint other than `GET /api/v1/audit*` (e.g. nested under
  each resource, like `GET /api/v1/products/{id}/audit`) - one flat, filterable endpoint is
  enough for this pass.
