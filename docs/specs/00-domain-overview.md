# Inventory domain - overview spec

Standing reference for the whole inventory domain. **All seven areas below are now
implemented** - each became its own numbered, contract-first feature folder under
`docs/specs/` (per [AGENTS.md](../../AGENTS.md)'s Spec Driven Development section) before
any of its code was written. This document remains the map and the whole-domain
cross-reference; each feature folder's own `requirements.md` + `openapi.yaml` is the
authoritative detail for that area. Platform-level, cross-cutting concerns that are not a
business-domain vertical slice -
[06-roles-and-permissions](06-roles-and-permissions/requirements.md),
[07-audit-trail](07-audit-trail/requirements.md), and
[10-reporting](10-reporting/requirements.md) (read-only, no new entity) - are deliberately
not numbered as a section here, same precedent as `06` from the start.

## 1. Catalog - products & categories

- **Category**: `id`, `name`, optional parent category (self-referencing, for a shallow
  hierarchy - e.g. "Electronics" > "Cables"). A category can't be deleted while products
  reference it.
- **Product**: `id`, `sku` (globally unique), `name`, `description`, `category`, `unitOfMeasure`
  (e.g. `EACH`, `KG`, `LITER` - a closed enum), `active` (soft lifecycle flag - an inactive
  product stays in historical stock movements but can't receive new stock or be ordered).

## 2. Warehouses & stock levels

- **Warehouse**: `id`, `name`, `address` (free text for v1 - no geocoding), `active`.
- **StockLevel**: the current on-hand quantity of one `Product` at one `Warehouse` -
  `(productId, warehouseId)` globally unique, `quantityOnHand`, `quantityReserved`
  (committed to an outbound order but not yet shipped - `quantityAvailable =
  quantityOnHand - quantityReserved` is derived, never stored). A `StockLevel` row is
  created the first time a product is received into a warehouse, not pre-created for every
  product/warehouse combination.

**This is the first implementable vertical** - see
[01-catalog-and-stock/requirements.md](01-catalog-and-stock/requirements.md).

## 3. Stock movements (ledger) - implemented, see docs/specs/02-stock-movements/

Every change to `quantityOnHand` is caused by exactly one **StockMovement** row - an
immutable, append-only ledger, never a direct update to `StockLevel.quantityOnHand` from
application code outside the movement-recording use case itself:

- **StockMovement**: `id`, `product`, `warehouse`, `type` (`RECEIPT`, `SHIPMENT`,
  `TRANSFER_IN`, `TRANSFER_OUT`, `ADJUSTMENT_INCREASE`, `ADJUSTMENT_DECREASE` - refined from
  a single `ADJUSTMENT` during that feature's spec, so "direction comes from type" is
  enforced by the type system rather than a separate sign field), `quantity` (always
  positive), `occurredAt`, `reference` (free-text: a PO number, a shipment id, a reason for
  a manual adjustment), `relatedMovementId` (for a transfer's matching `TRANSFER_IN`/
  `TRANSFER_OUT` pair). `StockLevel.quantityOnHand` is a projection recomputed (or
  incrementally updated, inside the same transaction, behind a pessimistic lock) from this
  ledger - never the other way around.
- A warehouse-to-warehouse transfer is two linked movements (`TRANSFER_OUT` at the source,
  `TRANSFER_IN` at the destination), never a single row that implicitly touches two
  warehouses - keeps the ledger's "one row, one warehouse's balance changes" invariant
  simple to audit.

## 4. Suppliers & purchase orders - implemented, see docs/specs/03-suppliers-and-purchase-orders/

- **Supplier**: `id`, `name`, contact info (email, phone - free text for v1).
- **PurchaseOrder**: `id`, `supplier`, `status` (`DRAFT` → `ORDERED` → `RECEIVED`, plus
  `CANCELLED` from either of the first two), `expectedDeliveryDate`, line items.
- **PurchaseOrderLine**: `product`, `quantityOrdered`, `quantityReceived`, `unitCost`.
  Receiving a line (fully or partially) creates a `RECEIPT` `StockMovement` for the received
  quantity at the order's destination warehouse - a `PurchaseOrder` moving to `RECEIVED`
  with every line fully received is the trigger, not a separate manual step.

## 5. Low-stock alerts / reorder thresholds - implemented, see docs/specs/04-low-stock-alerts/

- **ReorderThreshold**: `(productId, warehouseId)` → `reorderPoint`,
  `reorderQuantity` (the suggested quantity when triggering a reorder - advisory, not an
  automatic purchase order).
- A read endpoint (`GET /api/v1/stock-alerts` or similar, exact shape decided in that
  feature's own spec) lists every `StockLevel` currently at or below its
  `ReorderThreshold.reorderPoint`. No push notification/email in v1 - polling the endpoint
  is the only mechanism, consistent with
  [error-handling-and-logging.md](../conventions/error-handling-and-logging.md)'s note that
  no external notification service is wired up yet.

## 6. Sales orders & customers - implemented, see docs/specs/08-sales-orders-and-customers/

The outbound symmetric twin of section 4 - gives `StockLevel.quantityReserved` (section 2)
its first writer.

- **Customer**: `id`, `name`, contact info (email, phone) - identical shape to `Supplier`.
- **SalesOrder**: `id`, `customer`, `warehouse` (the source warehouse shipping against this
  order), `status` (`DRAFT` → `CONFIRMED` → `SHIPPED`, plus `CANCELLED` from either of the
  first two), `expectedShipDate`, line items. Confirming reserves stock
  (`quantityReserved += quantityOrdered` per line); cancelling a `CONFIRMED` order releases
  whatever of that reservation hasn't shipped yet.
- **SalesOrderLine**: `product`, `quantityOrdered`, `quantityShipped`, `unitPrice`. Shipping
  a line (fully or partially) releases that quantity's reservation and creates a `SHIPMENT`
  `StockMovement` - a `SalesOrder` moving to `SHIPPED` with every line fully shipped is the
  trigger, same "not a separate manual step" pattern as `PurchaseOrder`/`RECEIVED`.

## 7. Cycle counts - implemented, see docs/specs/09-cycle-counts/

A physical stock count/reconciliation workflow, one active state (`OPEN`) rather than a
multi-step chain.

- **CycleCount**: `id`, `warehouse`, `scope` (`FULL` - every product with stock at that
  warehouse - or `SELECTION` - just the given products), `status` (`OPEN` → `APPROVED`,
  posting adjustments, or `OPEN` → `CANCELLED`, posting nothing), line items.
- **CycleCountLine**: `product`, `countedQuantity` (nullable until recorded). Approving
  compares each line's `countedQuantity` against the **live** `quantityOnHand` at approval
  time (never a snapshot taken when the session opened) and posts exactly one
  `ADJUSTMENT_INCREASE`/`ADJUSTMENT_DECREASE` `StockMovement` per line that differs - through
  section 3's own recording service, never a second, parallel adjustment mechanism.

## Cross-cutting invariants

- `StockLevel.quantityOnHand` is never negative - a `StockMovement` that would drive it
  below zero (a `SHIPMENT`/`TRANSFER_OUT`/negative `ADJUSTMENT` exceeding on-hand) is
  rejected at the application layer, not merely discouraged.
- Every monetary value (`unitCost` on a purchase order line, `unitPrice` on a sales order
  line) is a `BigDecimal` with an explicit currency - a single currency is assumed for v1;
  multi-currency is out of scope until a real need appears.
- Every list endpoint across every area uses the one shared pagination envelope and
  filter-by-query-param convention from
  [conventions/api-design.md](../conventions/api-design.md) - never a bespoke shape per
  feature.
- Every mutating endpoint across every area requires at least the role
  [06-roles-and-permissions](06-roles-and-permissions/requirements.md) specifies, and
  produces the audit entries [07-audit-trail](07-audit-trail/requirements.md) specifies -
  both cross-cutting, not re-specified per area.

## Explicitly out of scope for now

- Multi-currency, multi-unit-of-measure conversion (e.g. ordering in boxes, stocking in
  units).
- Automated purchasing (auto-creating a `PurchaseOrder` when a `ReorderThreshold` is
  breached) - the alert is advisory only.
- Barcode/serial-number-level tracking (this domain tracks quantities, not individual
  unit identities).
