# Feature spec: Catalog bulk import/export (CSV)

Status: **implemented**. Scoped to `Product` only - the catalog entity most likely onboarded
in bulk (hundreds of SKUs at once); `Category`/`Warehouse` stay single-row CRUD, same as
today.

Scope: one of the SaaS-readiness gaps identified alongside
[11-user-management](../11-user-management/requirements.md) and
[14-webhooks](../14-webhooks/requirements.md) - onboarding a few hundred existing SKUs has
no way to avoid creating them one `POST` at a time today.

## Decision

- **CSV only**, `text/csv`, no Excel/XLSX - every other integration point in this project is
  already a plain, inspectable text format (JSON over HTTP); CSV is the bulk equivalent, and
  parsing XLSX would pull in a heavyweight dependency for no requested benefit.
- **Import is row-partial-success, never all-or-nothing.** Each row is validated and
  persisted independently, in its own transaction - one bad row (duplicate SKU, unknown
  category, malformed unit) is reported as a per-row error and every other valid row still
  commits. This matches how a human fixing a bulk upload actually works: fix the three bad
  rows the response pointed at, re-upload only those.
- **Row persistence reuses `ProductService.create` as-is** - same validation
  (`ProductSkuAlreadyExistsException`, `UnknownCategoryReferenceException`), same audit entry
  per created product (`AuditEntityType.PRODUCT`, `CREATED`). No parallel creation path to
  keep in sync with `01-catalog-and-stock`'s rules.
- **Category is referenced by name in the CSV, not by UUID.** A bulk CSV is prepared by a
  human (or exported from another system) who has no reason to know this application's
  internal UUIDs; `categoryName` is resolved to an existing `Category.id` per row, and
  "no category named X" is a per-row error (`CATEGORY_NOT_FOUND_BY_NAME`), never an
  auto-created category - creating catalog structure implicitly from a typo would be worse
  than rejecting the row.
- **A hard row cap (1000 data rows) rejects the whole request before parsing any row** -
  `422`, `VALIDATION_FAILED` (same shape as every other `InvalidReferenceException`-based
  422 in this project, field `file`) - a deliberate ceiling so one request can never tie up
  the database with an unbounded number of per-row transactions; more to import splits into
  multiple uploads. No file-size limit beyond Spring's own default multipart ceiling (1 MB)
  - a 1000-row product CSV is nowhere near that.
- **Export streams every active-or-not product the caller's filters match, CSV, same column
  order as import** - a round-trip (export, edit offline, re-import) must work without column
  remapping. Paginated internally against the repository in fixed-size batches (reusing
  `PaginationDefaults.MAX_PAGE_SIZE`) so one export never holds the whole catalog in memory
  at once, however many products exist.
- **No audit entries beyond the per-row `CREATED` already emitted by `ProductService.create`**
  - the import/export operations themselves are not separately audited entities; the created
  products already are.

## CSV format

Header row required, exact column order:

```
sku,name,description,categoryName,unitOfMeasure
```

- `sku`, `name`, `categoryName`, `unitOfMeasure`: required, same constraints as
  `CreateProductRequest` (`sku`/`name` non-blank, `sku` max 64 chars, `name` max 200).
- `description`: optional, blank allowed.
- `unitOfMeasure`: must be one of `EACH`/`KG`/`LITER` (`UnitOfMeasure`'s enum name, case
  -sensitive, exactly like every other enum this API transmits by name).
- Every imported row is created active (`Product.active = true`) - the CSV format has no
  `active` column; deactivating is still `PATCH /api/v1/products/{id}`, one row's worth of
  work a bulk tool isn't needed for.

## Endpoints

- `POST /api/v1/products/import` - `multipart/form-data`, one part named `file`
  (`text/csv`). `MANAGER` or above (same as `POST /api/v1/products`). Returns `200` (not
  `201` - the response describes a batch outcome, not one created resource) with:
  `{ "totalRows": n, "importedCount": n, "errors": [ { "row": n, "sku": "...", "message": "..." } ] }`.
  `row` is 1-based over data rows only (the header is not row 1). `sku` is the row's raw CSV
  value even when the row failed for a reason unrelated to the SKU, so the caller can locate
  it in their source file.
- `GET /api/v1/products/export` - `STAFF` or above (same as `GET /api/v1/products`), same
  `categoryId`/`active`/`sku` filters as `GET /api/v1/products` (default active-only unless
  `active=any`). Returns `200`, `text/csv`, `Content-Disposition: attachment;
  filename="products.csv"`, body in the format above (plus the header row).

## Acceptance criteria

1. A CSV with 10 valid rows and 0 existing conflicts imports all 10,
   `importedCount: 10`, `errors: []`.
2. A CSV where row 3 has a blank `sku` and row 7 references a `categoryName` that doesn't
   exist imports the other 8 rows; `importedCount: 8`, `errors` has exactly two entries for
   rows 3 and 7 with a human-readable `message` and no exception stack trace ever reaching
   the response body.
3. A CSV with two rows sharing the same `sku` imports the first and reports the second as
   `PRODUCT_SKU_ALREADY_EXISTS` in `errors` - partial success within a single file, not just
   against pre-existing data.
4. A 1001-data-row CSV is rejected wholesale, `422`, `VALIDATION_FAILED` - no row is
   persisted, not even the first 1000.
5. A caller below `MANAGER` gets `403` on import; a caller below `STAFF` gets `403` on
   export - consistent with `01-catalog-and-stock`'s existing role mapping for `Product`.
6. Every row imported this way produces exactly one `CREATED` audit entry, identical in shape
   to one created via `POST /api/v1/products` directly - no visible difference to
   `GET /api/v1/audit` between the two paths.
7. Export honors the same `active`-default and `categoryId`/`sku` filters as
   `GET /api/v1/products`, and its CSV re-imports successfully with zero errors when fed back
   into `POST /api/v1/products/import` unchanged (round-trip, modulo the now-pre-existing-SKU
   conflict that would legitimately report `PRODUCT_SKU_ALREADY_EXISTS` for every row -
   verified instead against a fresh, empty catalog).

## Out of scope for this feature

- Updating existing products via import (re-importing a known `sku` is a conflict, not an
  upsert) - a bulk *update* path is a different, not-yet-requested feature.
- Any entity other than `Product` (no bulk `Category`/`Warehouse`/`Supplier` import).
- XLSX or any non-CSV format.
- Async/background processing of the import itself - at the 1000-row ceiling, synchronous
  per-row processing inside the request is fast enough that a job queue would be premature
  infrastructure for a limit chosen specifically to keep it that way.
