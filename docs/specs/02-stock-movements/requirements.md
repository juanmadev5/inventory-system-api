# Feature spec: Stock movements (receiving, shipping, transfers, adjustments)

Status: **implemented and tested** (`domain`/`application`/`infrastructure`/`api` code,
`V3__stock_movements.sql`, `StockMovementControllerIntegrationTest` - 10 HTTP-level tests
covering every acceptance criterion below - and `StockMovementConcurrencyIntegrationTest`,
the dedicated concurrency test criterion 7 requires). Full reactor: 32 tests, all green.

Scope: [00-domain-overview.md](../00-domain-overview.md) section 3 - the append-only ledger
that is the only way `StockLevel.quantityOnHand` ever changes. Picked as the second vertical
(ahead of suppliers/purchase orders) because receiving a purchase order line is itself
defined, in the domain overview, as creating a `RECEIPT` movement - this feature is a
prerequisite for that one, not an arbitrary ordering choice.

## Why this vertical goes first

`StockLevel` currently has no write path at all (`01-catalog-and-stock` shipped it
read-only on purpose). This feature is what makes it real.

## Entities

- **StockMovementType** (enum, each with a fixed, implicit sign - never a separate
  "direction" field, so "always positive quantity, direction from type" from the domain
  overview is enforced by the type system, not a convention):
  `RECEIPT` (+), `SHIPMENT` (-), `TRANSFER_IN` (+), `TRANSFER_OUT` (-),
  `ADJUSTMENT_INCREASE` (+), `ADJUSTMENT_DECREASE` (-).
  - Refines the domain overview's single `ADJUSTMENT` into two signed types - the overview
    said "direction comes from type," which a single `ADJUSTMENT` value can't satisfy
    without a separate sign field. `00-domain-overview.md` is updated to match once this
    spec is approved.
- **StockMovement**: `id`, `productId`, `warehouseId`, `type`, `quantity` (always positive,
  `> 0`), `occurredAt` (defaults to now if omitted), `reference` (optional free text),
  `relatedMovementId` (nullable - set on both halves of a transfer pair, pointing at each
  other). Immutable once created: no update or delete endpoint, ever - it's a ledger.
- **StockLevel** gains its first writers in this feature. `(productId, warehouseId)` is
  still looked up or created on first movement, per `00-domain-overview.md`.

## Endpoints

- `POST /api/v1/stock-movements` - records a single-warehouse movement:
  `RECEIPT | SHIPMENT | ADJUSTMENT_INCREASE | ADJUSTMENT_DECREASE`. `TRANSFER_IN`/
  `TRANSFER_OUT` are **not** accepted here - only the transfer endpoint below can create
  them, since a lone half of a transfer pair is never valid.
- `POST /api/v1/stock-movements/transfers` - records a `TRANSFER_OUT` at the source
  warehouse and a `TRANSFER_IN` at the destination, atomically, linked via
  `relatedMovementId`. Either both are created or neither is.
- `GET /api/v1/stock-movements` - paginated, filterable by `productId`, `warehouseId`,
  `type`, `occurredFrom`, `occurredTo`.
- `GET /api/v1/stock-movements/{id}` - single movement.
- No `PATCH`/`DELETE` on any of the above.

## Acceptance criteria

1. `productId` and `warehouseId` (and, for a transfer, both warehouse ids) must reference
   an existing row - `422` / `VALIDATION_FAILED` otherwise (same pattern as
   `UnknownCategoryReferenceException` in `01-catalog-and-stock`).
2. `RECEIPT` and `ADJUSTMENT_INCREASE` against an **inactive product** are rejected - `409`,
   `PRODUCT_INACTIVE`. Matches the domain overview: an inactive product "can't receive new
   stock." `SHIPMENT`/`ADJUSTMENT_DECREASE` against an inactive product are allowed -
   shipping out the remainder of a discontinued product's stock is a legitimate case.
3. Any movement against an **inactive warehouse** (either side, for a transfer) is
   rejected - `409`, `WAREHOUSE_INACTIVE`.
4. A movement whose type has a negative sign (`SHIPMENT`, `ADJUSTMENT_DECREASE`,
   `TRANSFER_OUT`) is rejected with `409`, `INSUFFICIENT_STOCK` if it would drive that
   `(productId, warehouseId)`'s `quantityOnHand` below zero. The existing row (or an
   implicit zero, if none exists yet) is what's checked - never created as negative.
5. A transfer with `sourceWarehouseId == destinationWarehouseId` is rejected - `422` /
   `VALIDATION_FAILED`, field `destinationWarehouseId`.
6. A transfer is atomic: if the outbound leg would fail criterion 4, **neither** movement is
   created and `StockLevel` is unchanged at both warehouses.
7. **Concurrency**: two movements against the same `(productId, warehouseId)` submitted at
   the same time must never both read the same "current quantity" and both succeed when
   only one should (e.g. two shipments that would each individually be fine but together
   overdraw). The `StockLevel` row for that pair is locked (`SELECT ... FOR UPDATE`, i.e.
   JPA pessimistic write lock) for the duration of the transaction that applies a delta to
   it. **This needs its own concurrency test** (two threads/transactions racing to decrement
   the same row), per the project's testing policy on concurrency-sensitive code - a correct
   sequential test does not prove this.
8. A successful `POST` returns `201` with the created movement(s) and a `Location` header;
   the corresponding `StockLevel` row reflects the new `quantityOnHand` immediately
   (same transaction).

## Out of scope for this feature

- `quantityReserved` - untouched here; it belongs to a future sales-order-style feature
  that doesn't exist yet. Every `StockLevel` row's `quantityReserved` stays `0`.
- Undoing/reversing a movement - per the ledger's immutability, correcting a mistake is a
  new, opposite-direction movement (e.g. an `ADJUSTMENT_DECREASE` to cancel out a wrong
  `RECEIPT`), never an edit or delete of the original.
- Purchase-order-triggered receiving (`00-domain-overview.md` section 4) - that feature will
  call into this one's recording logic, not duplicate it, but is specified separately.
