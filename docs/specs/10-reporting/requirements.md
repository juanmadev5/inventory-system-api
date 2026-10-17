# Feature spec: Reporting

Status: **implemented and tested** (`ReportingService` in `application`, composing
`StockLevelRepository`, the new `PurchaseOrderLineCostRepository` port, and the new
`StockMovementRepository.findAllMatching`/`findMostRecentOccurredAt` methods - no new
persisted entity or migration). `ReportingController` in `api`. 142 tests across the `api`
module, all green, including `ReportingControllerIntegrationTest` (7 tests: cost averaging,
the null/excluded case, movement-type totals with a date-range filter, aging, and role
gating).

Scope: three read-only, aggregated views requested when scoping out the domain's remaining
gaps - inventory valuation, a movement summary, and stock aging. No new persisted entity;
every report is computed on demand from data `01`-`09` already own. Builds on
[06-roles-and-permissions](../06-roles-and-permissions/requirements.md) (`MANAGER`+ only -
these expose aggregated cost and operational data, more sensitive than any single raw
resource).

## Decision - deliberately basic, not a BI engine

- **Unit cost has no master-data home in this domain** - `Product` carries no cost field;
  `unitCost` only ever appears per line on a purchase order (what was actually paid on a
  specific order). This feature does not add one. Instead, the inventory valuation report
  uses the **average `unitCost` across every purchase-order line ever created for that
  product** (any order status) as a stand-in "current cost." A product that has never
  appeared on any purchase order has no determinable cost - its row reports `unitCost` and
  `value` as `null`, not `0`, so it is visibly excluded from the total rather than silently
  understating it.
- **Every report endpoint returning rows is still paginated**, per
  [api-design.md](../../conventions/api-design.md) - no exception for being a "report." Two
  consequences that are limitations, not bugs, and are documented here rather than
  papered over:
  - Inventory valuation's `pageTotalValue` sums only the current page's rows - it is
    deliberately not named `totalValue`, to not imply a grand total across every page. A
    caller wanting one pages through and sums client-side.
  - Stock aging's rows are **not** pre-sorted by staleness (that would require a
    database-level aggregate join this feature's first pass does not build) - a caller
    wanting "most stagnant first" sorts client-side after fetching what it needs.
- **Movement summary is the one non-paginated report** - it returns a single aggregated
  object (counts/quantities per `StockMovementType` across the whole filtered range), not a
  list of rows, so pagination does not apply to it at all.
- **No audit entries** - these endpoints only read, same as `StockAlertController`/
  `StockLevelController`.

## Endpoints

- `GET /api/v1/reports/inventory-valuation` - paginated. Filters: `warehouseId` (optional).
  Each row: `productId`, `warehouseId`, `categoryId`, `quantityOnHand`, `unitCost` (nullable),
  `value` (nullable, `quantityOnHand * unitCost`). Response also carries `pageTotalValue`
  (sum of this page's non-null `value`s) and `excludedCount` (how many rows on this page have
  no determinable cost).
- `GET /api/v1/reports/stock-movement-summary` - not paginated. Filters: `warehouseId`,
  `productId`, `occurredFrom`, `occurredTo` (all optional - omitted means "no bound" /
  "all time", same semantics as `02-stock-movements`' own filters). Returns one entry per
  `StockMovementType` (all six always present, `0`/`0` if no activity): `count`,
  `totalQuantity`.
- `GET /api/v1/reports/stock-aging` - paginated. Filters: `warehouseId` (optional), `asOf`
  (optional, defaults to now). Each row: `productId`, `warehouseId`, `quantityOnHand`,
  `lastMovementAt` (nullable - `null` if that `(productId, warehouseId)` has never had a
  movement), `daysSinceLastMovement` (nullable, derived from `asOf - lastMovementAt`).

## Roles (per [06-roles-and-permissions](../06-roles-and-permissions/requirements.md))

All three endpoints require `MANAGER` or above - no `STAFF` access, unlike every other `GET`
in this project. Reports surface aggregated cost and cross-order activity, which is a
manager-level concern, not a floor-operations one.

## Acceptance criteria

1. A caller with less than `MANAGER` gets `403`, `FORBIDDEN` on all three endpoints.
2. Inventory valuation: a product with at least one purchase-order line gets a non-null
   `unitCost` equal to the average of every `unitCost` ever recorded for it across all its
   purchase-order lines, and a non-null `value` equal to `quantityOnHand * unitCost`
   (rounded per the project's standard `BigDecimal` handling - no special rounding mode
   introduced).
3. Inventory valuation: a product with zero purchase-order lines gets `unitCost = null`,
   `value = null`, and is counted in `excludedCount`, not in `pageTotalValue`.
4. Movement summary: filtering by `occurredFrom`/`occurredTo`/`warehouseId`/`productId`
   restricts which movements are counted, identically to how
   `02-stock-movements`' own `GET /stock-movements` filters the same fields. A type with no
   matching movements reports `count: 0, totalQuantity: 0` - it is never omitted from the
   response.
5. Stock aging: a `(productId, warehouseId)` pair with at least one stock movement reports
   that movement's `occurredAt` as `lastMovementAt` and the correct whole-day difference from
   `asOf` as `daysSinceLastMovement`. A pair with no movement at all (a `StockLevel` row that
   somehow has quantity with no ledger history - not expected in practice, but not assumed
   impossible either) reports both as `null`.
6. Every error the shared `ProblemDetail` shape; every paginated report the shared
   pagination envelope.

## Out of scope for this feature

- A grand total for inventory valuation across every page - see "Decision".
- Database-level sorting of stock aging by staleness - see "Decision".
- Grouping/pivoting valuation server-side by category or warehouse (e.g. category subtotals)
  - the per-row `categoryId`/`warehouseId` and the `warehouseId` filter are enough for a
  client to group or narrow itself.
- Any export format (CSV, PDF) - JSON only, same as every other endpoint.
- Historical/point-in-time valuation (valuing stock as of a past date) - valuation always
  reflects current `quantityOnHand`; only stock aging accepts a historical `asOf`.
