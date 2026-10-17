# Feature spec: Sales orders & customers

Status: **implemented and tested** (`Customer`/`SalesOrder`/`SalesOrderLine` in `domain`,
`CustomerService`/`SalesOrderService`/`StockReservationService` in `application`, mirrored
JPA persistence in `infrastructure`, `CustomerController`/`SalesOrderController` in `api`).
`V7__sales_orders_and_customers.sql`. 121 tests across the `api` module, all green, including
`CustomerControllerIntegrationTest`, `SalesOrderControllerIntegrationTest` (19 tests covering
the full state machine, reservation/release bookkeeping, and role gating), and
`StockReservationConcurrencyIntegrationTest` (proves the same pessimistic-lock guarantee
`StockMovementConcurrencyIntegrationTest` proves for `quantityOnHand`, now for
`quantityReserved`).

Scope: closes the gap flagged when auditing domain completeness -
[02-stock-movements](../02-stock-movements/requirements.md) left `StockLevel.quantityReserved`
permanently at `0`, "belongs to a future sales-order-style feature that doesn't exist yet". This
is that feature. Mirrors [03-suppliers-and-purchase-orders](../03-suppliers-and-purchase-orders/requirements.md)
closely - a sales order is the outbound symmetric twin of a purchase order - and builds on
[06-roles-and-permissions](../06-roles-and-permissions/requirements.md) and
[07-audit-trail](../07-audit-trail/requirements.md) from day one rather than having them
retrofitted later.

## Entities

- **Customer**: `id`, `name` (required, globally unique), `email` (optional), `phone`
  (optional). Identical shape to `Supplier`.
- **SalesOrder**: `id`, `customerId`, `warehouseId` (the source warehouse shipping against this
  order), `status` (`DRAFT` → `CONFIRMED` → `SHIPPED`, or `CANCELLED` from `DRAFT`/`CONFIRMED`),
  `expectedShipDate` (optional), `lines` (at least one, fixed at creation - same "cancel and
  recreate instead" stance as purchase orders, see "Out of scope").
- **SalesOrderLine**: `id`, `productId`, `quantityOrdered`, `quantityShipped` (starts at `0`),
  `unitPrice` (`BigDecimal`, `>= 0` - symmetric with `unitCost` on a purchase order line; no
  invoicing/revenue recognition, just the per-unit price recorded on the order).

## State machine

```
DRAFT --confirmation--> CONFIRMED --(every line fully shipped)--> SHIPPED
  |                         |
  +------ cancellation -----+--> CANCELLED
```

- `SHIPPED` is never set directly - automatic once the last line reaches
  `quantityShipped == quantityOrdered`, same as `PurchaseOrderStatus.RECEIVED`.
- **Confirming** (`DRAFT` → `CONFIRMED`) reserves stock: for every line, `quantityReserved` at
  `(productId, warehouseId)` increases by `quantityOrdered`. If any line's reservation would
  exceed that pair's available quantity (`quantityOnHand - quantityReserved`), `409`,
  `INSUFFICIENT_STOCK` - and, since this all happens inside one transaction, no line's
  reservation from this same confirmation is left partially applied.
- **Shipping a line** is only valid while the order is `CONFIRMED`. It releases exactly the
  shipped quantity from that line's reservation (`quantityReserved` decreases by the shipped
  amount) and records a `SHIPMENT` `StockMovement` for the same quantity at the order's
  `warehouseId` (via `02-stock-movements`' recording service - same transaction, same
  pessimistic lock on `StockLevel`, never duplicated logic) - symmetric with how receiving a
  purchase order line calls into that same service for a `RECEIPT`.
- **Cancelling** is only valid from `DRAFT` or `CONFIRMED`. Cancelling a `CONFIRMED` order
  (partially shipped or not) releases each line's *remaining* reservation
  (`quantityOrdered - quantityShipped`) but does **not** reverse stock already shipped -
  symmetric with a purchase order's "cancelling a partially-received order does not reverse
  stock already received."

## Stock reservation mechanism

`StockLevel.quantityReserved` gets its first writer in this feature, via a new
`StockReservationService` (`application/.../stock/`) with `reserve`/`release`, both built on the
same `StockLevelRepository.lockOrCreateForUpdate` pessimistic-lock primitive
`StockMovementRecordingService` already uses for `quantityOnHand` - the same concurrency
guarantee from `02-stock-movements`' criterion 7 extends to reservations: two concurrent
confirmations against the same `(productId, warehouseId)` cannot both read the same stale
available quantity and both succeed when only one should.

`reserve` throws `InsufficientStockException` (the same `409`/`INSUFFICIENT_STOCK` 
`02-stock-movements` already defines) when the requested quantity exceeds what's available;
`release` is only ever called by this feature's own code with a quantity it already knows is
currently reserved (the unshipped remainder of a line it owns), so it performs no additional
validation - an internal invariant, not a system boundary, per
[engineering.md](../../conventions/engineering.md)'s error-handling stance.

A reserved-but-inactive product is **not** specially blocked anywhere in this feature:
`02-stock-movements` already allows `SHIPMENT` against an inactive product ("shipping out the
remainder of a discontinued product's stock is a legitimate case"), so blocking the reservation
step that precedes it would be inconsistent. Only the order's `warehouseId` must be active
(checked at creation, same as a purchase order's destination warehouse).

## Endpoints

- `POST /api/v1/customers`, `GET /api/v1/customers` (paginated, filter by `name`),
  `GET /api/v1/customers/{customerId}`, `PATCH /api/v1/customers/{customerId}` - identical
  shape to `/api/v1/suppliers`. No `DELETE`.
- `POST /api/v1/sales-orders` - creates a `DRAFT` order with its lines, atomically.
- `GET /api/v1/sales-orders` (paginated, filter by `customerId`, `warehouseId`, `status`),
  `GET /api/v1/sales-orders/{salesOrderId}`.
- `PATCH /api/v1/sales-orders/{salesOrderId}` - **only** `expectedShipDate`, and **only** while
  `DRAFT`. Lines are not editable after creation.
- `POST /api/v1/sales-orders/{salesOrderId}/confirmation` - `DRAFT` → `CONFIRMED`, reserves
  stock.
- `POST /api/v1/sales-orders/{salesOrderId}/cancellation` - `DRAFT`/`CONFIRMED` → `CANCELLED`,
  releases any remaining reservation.
- `POST /api/v1/sales-orders/{salesOrderId}/lines/{lineId}/shipments` - records shipping
  `quantity` units (full or partial) of that line. Body: `{ "quantity": n }`.

## Roles (per [06-roles-and-permissions](../06-roles-and-permissions/requirements.md))

| Endpoint | Required role |
|---|---|
| `GET /api/v1/customers*` | STAFF |
| `POST`/`PATCH /api/v1/customers*` | MANAGER |
| `GET /api/v1/sales-orders*` | STAFF |
| `POST`/`PATCH /api/v1/sales-orders*`, `.../confirmation`, `.../cancellation` | MANAGER |
| `POST /api/v1/sales-orders/{id}/lines/{lineId}/shipments` | STAFF (shipping executes an
  already-confirmed order, not a decision to sell - symmetric with receiving a purchase order
  line) |

## Audit (per [07-audit-trail](../07-audit-trail/requirements.md))

`Customer` and `SalesOrder` are added to `AuditEntityType`, wired into `CustomerService` and
`SalesOrderService` exactly like every other audited entity - a `CREATED` entry on creation, an
`UPDATED` entry (old/new snapshot) on every edit, confirmation, cancellation, and shipment.

## Acceptance criteria

### Customers

1. Creating a customer with a `name` already used by another customer fails with `409`,
   `CUSTOMER_NAME_ALREADY_EXISTS`.
2. `email`, if provided, must be a syntactically valid email - `400` (Bean Validation) otherwise.

### Creating a sales order

1. `customerId` must reference an existing customer - `422` / `VALIDATION_FAILED`
   otherwise.
2. `warehouseId` must reference an existing warehouse (`422` if unknown, `409`
   `WAREHOUSE_INACTIVE` if it exists but is inactive).
3. At least one line is required - `400` (Bean Validation, `@NotEmpty`) otherwise.
4. Each line's `productId` must reference an existing product - `422` otherwise.
   The product does **not** need to be active.
5. `quantityOrdered` must be `> 0`; `unitPrice` must be `>= 0` - `400` (Bean Validation)
   otherwise.
6. A new order is always created in `DRAFT`, with every line's `quantityShipped` at `0`.

### Editing

1. `PATCH` (only `expectedShipDate`) is rejected with `409`, `SALES_ORDER_NOT_EDITABLE` unless
   the order is `DRAFT`.
2. There is no endpoint to add, remove, or change a line after creation.

### Confirmation and cancellation

1. `POST .../confirmation` is rejected with `409`, `SALES_ORDER_NOT_DRAFT` unless the order is
   currently `DRAFT`.
2. A successful confirmation increases `quantityReserved` by `quantityOrdered` for every line's
   `(productId, warehouseId)`. If any line cannot be reserved in full, `409`,
   `INSUFFICIENT_STOCK`, and **no** line's reservation from this attempt is applied.
3. `POST .../cancellation` is rejected with `409`, `SALES_ORDER_NOT_CANCELLABLE` unless the order
   is currently `DRAFT` or `CONFIRMED`.
4. Cancelling a `CONFIRMED` order releases `quantityOrdered - quantityShipped` from
   `quantityReserved` for every line. Cancelling a `DRAFT` order releases nothing (nothing was
   ever reserved).

### Shipping a line

1. Rejected with `409`, `SALES_ORDER_NOT_CONFIRMED` unless the order is currently `CONFIRMED`.
2. `lineId` must belong to the given `salesOrderId` - `404` otherwise.
3. `quantity` must be `> 0` - `400` (Bean Validation) otherwise.
4. If `quantity` would push that line's `quantityShipped` above its `quantityOrdered`, rejected
   with `409`, `SHIPMENT_EXCEEDS_ORDERED_QUANTITY` - nothing is partially applied.
5. Otherwise: releases `quantity` from the line's reservation, then records a `SHIPMENT`
   `StockMovement` (via `02-stock-movements`' recording service) for `quantity` units of that
   line's product at the order's `warehouseId`, with `reference` set to the sales order's id.
   The line's `quantityShipped` is incremented by `quantity`.
6. After criterion 5, if **every** line now has `quantityShipped == quantityOrdered`, the
   order's status becomes `SHIPPED` automatically, in the same transaction. Otherwise it stays
   `CONFIRMED`.

### Cross-cutting

1. List endpoints use the shared pagination envelope; every error the shared `ProblemDetail`
   shape.
3. Role requirements per the table above; every write produces the audit entries per
   [07-audit-trail](../07-audit-trail/requirements.md).

## Out of scope for this feature

- Editing a sales order's lines after creation - cancel and recreate instead.
- Reversing stock already shipped when a partially-shipped order is cancelled.
- Invoicing, payment, or revenue recognition - `unitPrice` is recorded data only.
- Any reservation expiry/TTL - a `CONFIRMED` order holds its reservation indefinitely until
  shipped or cancelled; no background job releases a stale one.
- Any connection to purchase orders or low-stock alerts (e.g. auto-generating a purchase order
  when a sales order can't be confirmed) - that is the explicitly out-of-scope "automated
  purchasing" from [00-domain-overview.md](../00-domain-overview.md), unchanged.
