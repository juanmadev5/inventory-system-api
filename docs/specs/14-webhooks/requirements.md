# Feature spec: Webhooks

Status: **implemented**. The last of the SaaS-readiness gaps identified alongside
[11-user-management](../11-user-management/requirements.md) and
[12-catalog-bulk-import-export](../12-catalog-bulk-import-export/requirements.md) - today
the only way to learn about a state change is to poll.

## Decision - transactional outbox + scheduled dispatcher, never a synchronous HTTP call

The hard constraint behind every choice below: **the API must never do unnecessary work or
abusively occupy its connection pool.** A synchronous "call the webhook URL inside the
request that caused the event" design was rejected for exactly that reason - a slow or down
third-party endpoint would make this API's own request latency (and connection-pool
occupancy) depend on someone else's server.

- **Write path (inside the request, cheap, no network I/O):** the four trigger points below
  write one row to `webhook_outbox_events` in the *same transaction* as the business state
  change that caused it - either both commit or neither does. No HTTP call happens here.
- **Read/deliver path (background, bounded, no request ever waits on it):** a
  `@Scheduled` job (`WebhookDispatchService`), independent of any HTTP request, wakes up
  every `app.webhooks.dispatch-interval-ms` (default 15000) and:
  1. Loads up to `app.webhooks.dispatch-batch-size` (default 50) due, pending rows in a
     short, bounded read - never the whole table.
  2. For each row, loads the enabled subscriptions matching the row's event type.
  3. **Releases the DB connection before making any HTTP call** - the POST to a
     subscription's webhook URL happens with no transaction open and a short timeout
     (`app.webhooks.delivery-timeout-ms`,
     default 5000, connect and read both), so a slow or unreachable receiver only occupies
     this feature's own `RestClient`/HTTP connection, never a pooled JDBC connection.
  4. Records the outcome in its own short, separate transaction: all-subscriptions-succeeded
     marks the row `DELIVERED`; any failure increments `attempts` and schedules
     `next_attempt_at` with exponential backoff (`30s * 2^attempts`, capped at 1 hour), up to
     `app.webhooks.max-attempts` (default 10) before giving up and marking it `FAILED`.
  - A row with zero matching enabled subscriptions is marked `DELIVERED` immediately (no
    subscription currently cares about that event type - not a failure).
  - Retrying a row that partially succeeded (some subscriptions already got it, one didn't)
    re-POSTs to **every** matching subscription, not just the failed one - the alternative
    (per-subscription delivery tracking) is real complexity this feature doesn't need yet,
    given this application is expected to have at most a handful of subscriptions
    configured. Receivers are expected to be idempotent on `eventId` (every payload carries
    one), exactly like every other webhook provider's contract.
- **Payload signing**: every delivery includes `X-Webhook-Signature: sha256=<hex>`, an
  HMAC-SHA256 of the raw JSON body using the subscription's own secret - the same pattern
  every major webhook provider uses, so a receiver can verify authenticity without this
  platform needing TLS client certs or IP allowlisting. The secret is generated server-side
  on subscription creation and returned exactly once, identical in spirit to the
  one-time-reveal temporary password in
  [11-user-management](../11-user-management/requirements.md) - never retrievable again,
  never logged.
- **Four event types for this iteration**, chosen from what already existed: `STOCK_ALERT_ACTIVATED`
  (a product's `quantityOnHand` newly crosses *below* its configured reorder point - this
  needed new logic, since [04-low-stock-alerts](../04-low-stock-alerts/requirements.md)'s
  `StockAlert` is a computed, non-persisted projection with no "activated" moment today;
  the check now lives in `StockMovementRecordingService.applyDeltaOrThrow`, the sole writer
  of `quantityOnHand`), `SALES_ORDER_SHIPPED` (every line fully shipped - `SalesOrderService.shipLine`),
  `PURCHASE_ORDER_RECEIVED` (every line fully received - `PurchaseOrderService.receiveLine`),
  `CYCLE_COUNT_APPROVED` (`CycleCountService.approve`).

## Subscription management

- `POST /api/v1/webhooks/subscriptions` - `ADMIN` only. Body: `{ "url": "...", "eventTypes": [...] }`
  (at least one of the four event type names). Returns `201` with the created subscription
  and its `secret` (once).
- `GET /api/v1/webhooks/subscriptions` - `ADMIN` only, paginated.
- `PATCH /api/v1/webhooks/subscriptions/{id}` - `ADMIN` only. Body: any of `url`, `eventTypes`,
  `enabled` (disable without deleting - same "deactivate, don't delete" stance as
  [11-user-management](../11-user-management/requirements.md)'s users).
- No secret rotation endpoint and no delivery-history endpoint in this iteration - losing a
  secret means deleting the subscription (not built either - update `enabled: false` is the
  only lifecycle operation) and creating a new one; delivery history is visible only as this
  application's own logs (see "Out of scope").

## Event payload shape

Every delivery's JSON body:

```json
{
  "eventId": "<uuid, stable across retries>",
  "eventType": "STOCK_ALERT_ACTIVATED",
  "occurredAt": "2026-01-01T00:00:00Z",
  "data": { "...": "event-type-specific fields" }
}
```

`data` per event type:

- `STOCK_ALERT_ACTIVATED`: `productId`, `warehouseId`, `quantityOnHand`, `reorderPoint`.
- `SALES_ORDER_SHIPPED`: `salesOrderId`, `customerId`, `warehouseId`.
- `PURCHASE_ORDER_RECEIVED`: `purchaseOrderId`, `supplierId`, `warehouseId`.
- `CYCLE_COUNT_APPROVED`: `cycleCountId`, `warehouseId`.

## Acceptance criteria

1. Crossing a reorder point (an `applyDeltaOrThrow` call that takes `quantityOnHand` from
   `>= reorderPoint` to `< reorderPoint`) writes exactly one `STOCK_ALERT_ACTIVATED` outbox
   row in the same transaction as the stock movement; a movement that leaves it unchanged on
   the already-below side, or moves it further below without crossing from above, writes
   none (only the *crossing* is the event, not "is currently low").
2. Fully shipping a sales order's last remaining line writes one `SALES_ORDER_SHIPPED` row;
   a partial shipment that leaves the order `CONFIRMED` writes none.
3. Fully receiving a purchase order's last remaining line writes one `PURCHASE_ORDER_RECEIVED`
   row; a partial receipt writes none.
4. Approving a cycle count writes one `CYCLE_COUNT_APPROVED` row, unconditionally.
5. Zero enabled subscriptions matching an event type still gets that event's outbox row
   marked `DELIVERED` on the next dispatch tick - never stuck `PENDING` forever.
6. A subscription receives a POST whose `X-Webhook-Signature` header, verified against the
   subscription's own secret, matches an HMAC-SHA256 of the exact raw body bytes delivered.
7. A receiver returning a non-2xx status, or not responding within the configured timeout,
   causes `attempts` to increment and `next_attempt_at` to move into the future -
   corresponding event is retried on a later tick, not immediately, and not dropped.
8. After `app.webhooks.max-attempts` failed attempts, the row is marked `FAILED` and no
   longer picked up by the dispatcher.
9. No HTTP call to a subscription's webhook URL ever happens inside the request that triggered the
   underlying business event - verified by the business endpoint's response time being
   unaffected by an unreachable/slow webhook URL.
10. A caller below `ADMIN` gets `403` on every `/api/v1/webhooks/subscriptions` endpoint.

## Out of scope for this feature

- A delivery-history/audit endpoint (`GET .../deliveries`) - logs are the only record for
  now, consistent with the project's "good detailed logs are enough" observability stance.
- Secret rotation - delete and recreate instead.
- Per-subscription delivery tracking when more than one subscription exists for the same
  event type - see "Decision".
- Any event type beyond the four listed - the next one follows the same spec-first process.
- Manual "replay this failed event" endpoint.
- Configurable retry/backoff policy per subscription - the four `app.webhooks.*` settings
  are global, not per-subscription.
