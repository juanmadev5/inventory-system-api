# Feature spec: Catalog & stock (categories, products, warehouses, stock levels)

Status: **implemented and tested** (`domain`/`application`/`infrastructure`/`api` code,
`V2__catalog_and_stock.sql`, `CategoryControllerIntegrationTest`,
`ProductControllerIntegrationTest`, `WarehouseControllerIntegrationTest` - full reactor
green). `StockLevel` has no controller-level test yet since it has no write path to
exercise in this feature.

Scope: the first implementable vertical from
[00-domain-overview.md](../00-domain-overview.md) sections 1-2. Stock **movements**
(receiving, shipping, transfers, adjustments), suppliers/purchase orders, and reorder
alerts are explicitly out of scope here - separate, later feature folders.

## Entities (this feature introduces `domain/` - see
[architecture.md](../../conventions/architecture.md))

- `Category`: `id` (UUID), `name` (required, globally unique), `parentCategoryId`
  (nullable, self-reference).
- `Product`: `id`, `sku` (required, globally unique), `name` (required), `description`
  (optional), `categoryId` (required, must reference an existing category), `unitOfMeasure`
  (enum: `EACH`, `KG`, `LITER`), `active` (boolean, defaults `true`).
- `Warehouse`: `id`, `name` (required, globally unique), `address` (optional free text),
  `active` (boolean, defaults `true`).
- `StockLevel`: `id`, `productId`, `warehouseId`, `quantityOnHand`, `quantityReserved`.
  **Read-only in this feature** - no row is ever created or mutated here; rows start
  appearing once the Stock Movements feature (next iteration) ships. Shipped now so the
  read API contract exists and the pagination/filtering pattern is proven before that
  feature adds writes to it.

## Acceptance criteria

### Categories

1. Creating a category with a `name` already used by another category fails with `409`
   and code `CATEGORY_NAME_ALREADY_EXISTS`.
2. `parentCategoryId`, if provided, must reference an existing category - otherwise `422`
   / `VALIDATION_FAILED` with a field-level error.
3. A category referenced by at least one product cannot be deleted - `409`,
   `CATEGORY_IN_USE`.
4. Listing categories is paginated and supports filtering by `name` (partial match) and
   `parentCategoryId`.

### Products

1. Creating a product with a `sku` already used by another product fails with `409`,
   `PRODUCT_SKU_ALREADY_EXISTS`.
2. `categoryId` must reference an existing category - `422` / `VALIDATION_FAILED`
   otherwise.
3. `unitOfMeasure` is validated against the closed enum - an unrecognized value is `400`
   (malformed request), not `422`.
4. Deactivating a product (`PATCH` with `active: false`) is not a deletion - the product
   stays visible in history and in a direct `GET` by id, but is excluded from the default
   product list unless the caller explicitly asks for inactive products too
   (`?active=false` or `?active=any`).
5. Listing products is paginated and supports filtering by `categoryId`, `active`, and `sku`
   (exact match).

### Warehouses

1. Creating a warehouse with a `name` already used by another warehouse fails with `409`,
   `WAREHOUSE_NAME_ALREADY_EXISTS`.
2. Listing warehouses is paginated and supports filtering by `active`.

### Stock levels (read-only)

1. `GET /api/v1/stock-levels` is paginated and supports filtering by `productId` and
   `warehouseId`.
2. `GET /api/v1/stock-levels/{id}` returns `404` if the row doesn't exist.

### Cross-cutting (every resource above)

1. Every write (`POST`/`PATCH`) requires an authenticated caller - an unauthenticated
   request is `401`.
2. Every list endpoint returns the shared `PageResponse` envelope; every error the shared
   `ProblemDetail` shape with a stable `code`.

## Out of scope for this feature

- Any endpoint that changes `StockLevel.quantityOnHand` (belongs to Stock Movements).
- Soft-deleting/deactivating a category or warehouse (only products get the `active` flag
  in this feature - extend later if a real need appears, per Scope discipline).
- Bulk import/export of any of these resources.
