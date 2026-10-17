# Feature spec: Suppliers & purchase orders

Status: **implemented and tested** (`domain`/`application`/`infrastructure`/`api` code,
`V4__suppliers_and_purchase_orders.sql`, `SupplierControllerIntegrationTest`,
`PurchaseOrderControllerIntegrationTest` covering the full state machine). Full reactor: 52
tests, all green.

One correction made during implementation: "at least one line is required" (see criterion 3
under "Creating a purchase order" below) ships as `400` via Bean Validation
(`@NotEmpty` on the request's `lines` field), not `422`/`VALIDATION_FAILED` as originally
drafted - a missing/empty required field is a malformed-request concern (same bucket as a
blank `name` elsewhere in this project), not a reference-to-something-that-doesn't-exist
one. The criterion's intent (reject an order with no lines) is unchanged.

Scope: [00-domain-overview.md](../00-domain-overview.md) section 4. Builds directly on
[02-stock-movements](../02-stock-movements/requirements.md) - receiving a purchase order
line calls into that feature's movement-recording use case, never duplicates its logic
(validation, the pessimistic lock, the ledger insert all stay in one place).

## Entities

- **Supplier**: `id`, `name` (required, globally unique), `email` (optional), `phone`
  (optional).
- **PurchaseOrder**: `id`, `supplierId`, `warehouseId` (the destination warehouse receiving
  stock against this order), `status` (`DRAFT` → `ORDERED` → `RECEIVED`, or `CANCELLED` from
  `DRAFT`/`ORDERED`), `expectedDeliveryDate` (optional), `lines` (at least one, fixed at
  creation - see "Editing" below).
- **PurchaseOrderLine**: `id`, `productId`, `quantityOrdered`, `quantityReceived` (starts at
  `0`), `unitCost`. Persisted in its own table (`purchase_order_lines`, plain
  `purchase_order_id` FK column) rather than a JPA `@OneToMany` - the adapter composes the
  aggregate by hand, same as every other feature in this project; no Hibernate relationship
  magic leaks into `domain`.

## State machine

```
DRAFT --placement--> ORDERED --(every line fully received)--> RECEIVED
  |                      |
  +---- cancellation ----+--> CANCELLED
```

- `RECEIVED` is never set directly by a client request - it's the automatic result of the
  last line reaching `quantityReceived == quantityOrdered`, exactly as
  [00-domain-overview.md](../00-domain-overview.md) specifies ("not a separate manual
  step").
- Receiving a line is only valid while the order is `ORDERED`.
- Cancelling is only valid from `DRAFT` or `ORDERED`. Cancelling a partially-received
  `ORDERED` order does **not** reverse the stock already received - out of scope, see
  below.

## Endpoints

- `POST /api/v1/suppliers`, `GET /api/v1/suppliers` (paginated, filter by `name`),
  `GET /api/v1/suppliers/{supplierId}`, `PATCH /api/v1/suppliers/{supplierId}` - same shape
  as every other simple resource in this project (see `01-catalog-and-stock`). No `DELETE`.
- `POST /api/v1/purchase-orders` - creates a `DRAFT` order with its lines, atomically.
- `GET /api/v1/purchase-orders` (paginated, filter by `supplierId`, `warehouseId`, `status`),
  `GET /api/v1/purchase-orders/{purchaseOrderId}`.
- `PATCH /api/v1/purchase-orders/{purchaseOrderId}` - **only** `expectedDeliveryDate`, and
  **only** while `DRAFT`. Lines are not editable after creation (see "Editing" below).
- `POST /api/v1/purchase-orders/{purchaseOrderId}/placement` - `DRAFT` → `ORDERED`.
- `POST /api/v1/purchase-orders/{purchaseOrderId}/cancellation` - `DRAFT`/`ORDERED` →
  `CANCELLED`.
- `POST /api/v1/purchase-orders/{purchaseOrderId}/lines/{lineId}/receipts` - records
  receiving `quantity` units (full or partial) of that line. Body: `{ "quantity": n }`.

## Acceptance criteria

### Suppliers

1. Creating a supplier with a `name` already used by another supplier fails with `409`,
   `SUPPLIER_NAME_ALREADY_EXISTS`.
2. `email`, if provided, must be a syntactically valid email - `400` (Bean Validation)
   otherwise.

### Creating a purchase order

1. `supplierId` must reference an existing supplier - `422` / `VALIDATION_FAILED`
   otherwise.
2. `warehouseId` must reference an existing warehouse (`422` if unknown,
   `409` `WAREHOUSE_INACTIVE` if it exists but is inactive - reusing exactly the checks
   `02-stock-movements` already has for this).
3. At least one line is required - `400` (Bean Validation, `@NotEmpty`) otherwise. See the
   status note above.
4. Each line's `productId` must reference an existing product - `422`
   otherwise (reusing `02-stock-movements`' `UnknownProductReferenceException`). The product
   does **not** need to be active to be ordered - only receiving stock against an inactive
   product is blocked (that rule already lives in `02-stock-movements` and fires
   automatically when a line is received).
5. `quantityOrdered` must be `> 0`; `unitCost` must be `>= 0` - `400` (Bean Validation)
   otherwise.
6. A new order is always created in `DRAFT`, with every line's `quantityReceived` at `0`.

### Editing

1. `PATCH` (only `expectedDeliveryDate`) is rejected with `409`,
   `PURCHASE_ORDER_NOT_EDITABLE` unless the order is `DRAFT`.
2. There is no endpoint to add, remove, or change a line after creation. Deliberately out
   of scope - see below.

### Placement and cancellation

1. `POST .../placement` is rejected with `409`, `PURCHASE_ORDER_NOT_DRAFT` unless the order
   is currently `DRAFT`.
2. `POST .../cancellation` is rejected with `409`, `PURCHASE_ORDER_NOT_CANCELLABLE` unless
   the order is currently `DRAFT` or `ORDERED`.

### Receiving a line

1. Rejected with `409`, `PURCHASE_ORDER_NOT_ORDERED` unless the order is currently `ORDERED`.
2. `lineId` must belong to the given `purchaseOrderId` - `404` otherwise.
3. `quantity` must be `> 0` - `400` (Bean Validation) otherwise.
4. If `quantity` would push that line's `quantityReceived` above its `quantityOrdered`,
   rejected with `409`, `RECEIPT_EXCEEDS_ORDERED_QUANTITY` - **nothing is partially
   applied**.
5. Otherwise: records a `RECEIPT` `StockMovement` (via `02-stock-movements`' recording
   service - same transaction, same pessimistic lock on `StockLevel`) for `quantity` units
   of that line's product at the order's `warehouseId`, with `reference` set to the
   purchase order's id. The line's `quantityReceived` is incremented by `quantity`.
6. After applying criterion 5, if **every** line on the order now has
   `quantityReceived == quantityOrdered`, the order's status becomes `RECEIVED`
   automatically, in the same transaction. Otherwise it stays `ORDERED`.
7. Receiving against an inactive product fails exactly as `02-stock-movements` already
   specifies (`409`, `PRODUCT_INACTIVE`) - this feature adds no new check for it, it
   inherits the one that already exists.

### Cross-cutting

1. List endpoints use the shared pagination envelope; every error the shared
   `ProblemDetail` shape.

## Out of scope for this feature

- Editing a purchase order's lines after creation (quantity, product, or adding/removing a
  line) - cancel and recreate instead, per the "Editing" section above.
- Reversing stock already received when a partially-received order is cancelled.
- Multi-currency `unitCost` - a single currency is assumed, per
  [00-domain-overview.md](../00-domain-overview.md).
- Any connection to the low-stock-alerts feature (not specified yet) - a received purchase
  order does not automatically clear or adjust a reorder threshold in this pass.
