# Feature spec: Low-stock alerts / reorder thresholds

Status: **implemented and tested** (`domain`/`application`/`infrastructure`/`api` code,
`V5__reorder_thresholds.sql`, `ReorderThresholdControllerIntegrationTest`,
`StockAlertControllerIntegrationTest` - including the "no StockLevel row yet counts as
zero" edge case from criterion 2 below). Full reactor: 64 tests, all green. This was the
last unspecified area of [00-domain-overview.md](../00-domain-overview.md) - the whole
domain is now implemented.

Scope: [00-domain-overview.md](../00-domain-overview.md) section 5 - the last unspecified
area of the domain. Purely additive/read-side on top of `01-catalog-and-stock` and
`02-stock-movements`: it configures thresholds and reads `StockLevel`, never writes to it.

## Entities

- **ReorderThreshold**: `id`, `productId`, `warehouseId`, `reorderPoint` (`>= 0` - the
  on-hand quantity at or below which the pair is "low"), `reorderQuantity` (`> 0` - the
  advisory suggested reorder amount; nothing acts on it automatically, per the domain
  overview). One threshold per `(productId, warehouseId)`.
- **StockAlert**: not a persisted entity - a read-only projection joining every configured
  `ReorderThreshold` against its `StockLevel` (`productId`, `warehouseId`,
  `quantityOnHand`, `reorderPoint`, `reorderQuantity`). A `(productId, warehouseId)` with no
  configured threshold never appears here, no matter how low its actual quantity - no
  threshold means no alert, by design.

## Endpoints

- `POST /api/v1/reorder-thresholds`, `GET /api/v1/reorder-thresholds` (paginated, filter by
  `productId`/`warehouseId`), `GET /api/v1/reorder-thresholds/{thresholdId}`,
  `PATCH /api/v1/reorder-thresholds/{thresholdId}` (updates `reorderPoint`/
  `reorderQuantity`) - same shape as every other simple resource in this project. No
  `DELETE`, consistent with `Category`/`Warehouse`/`Supplier`.
- `GET /api/v1/stock-alerts` (paginated, filter by `warehouseId`) - the only new read
  surface this feature adds. No create/update/delete: an "alert" isn't a resource anyone
  creates, it's a computed state.

## Acceptance criteria

### Reorder thresholds

1. `productId` and `warehouseId` must each reference an existing row - `422`
   / `VALIDATION_FAILED` otherwise (reusing the existing
   `UnknownProductReferenceException`/`UnknownWarehouseReferenceException` from
   `02-stock-movements`). Neither needs to be **active** - configuring an alert for a
   currently-inactive product/warehouse is allowed (e.g. setting it up ahead of
   reactivation).
2. A second threshold for the same `(productId, warehouseId)` is rejected - `409`,
   `REORDER_THRESHOLD_ALREADY_EXISTS`.
3. `reorderPoint < 0` or `reorderQuantity <= 0` - `400` (Bean Validation).
4. `PATCH` accepts `reorderPoint` and/or `reorderQuantity`; omitted fields are unchanged
   (same "null means no change" convention used everywhere else in this project).

### Stock alerts

1. A `(productId, warehouseId)` pair appears in `GET /api/v1/stock-alerts` **if and only
   if** it has a configured `ReorderThreshold` **and** its current `quantityOnHand` is
   `<=` that threshold's `reorderPoint`.
2. A pair with a configured threshold but **no `StockLevel` row at all yet** (nothing has
   ever been received there) counts as `quantityOnHand = 0` for this comparison - it is the
   most urgent case, not an absent one, and must appear whenever `reorderPoint >= 0` (which
   it always is).
3. Each result row carries `quantityOnHand`, `reorderPoint`, and `reorderQuantity` together
   - a client reads one response, never a separate stock-level lookup, to decide how much
   to reorder.
4. No push notification, email, or background job - this is a pull-only read endpoint, per
   the domain overview ("polling the endpoint is the only mechanism").

### Cross-cutting

1. List endpoints use the shared pagination envelope; every error the shared
   `ProblemDetail` shape.

## Out of scope for this feature

- Any automated action when a pair crosses its threshold (auto-creating a purchase order,
  sending an email/webhook) - purely advisory, per the domain overview.
- A threshold that varies over time (e.g. seasonal) - one static `reorderPoint`/
  `reorderQuantity` per pair.
- Deleting a threshold - `PATCH` is the only mutation after creation, consistent with the
  rest of this project's simple resources.
