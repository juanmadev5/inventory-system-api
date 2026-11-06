# Feature spec: Reference-data caching (Redis)

Status: **implemented and tested** (`infrastructure/.../platform/cache/`, `@Cacheable`/
`@CacheEvict` on the three repository adapters, `ReferenceDataCachingIntegrationTest`,
`RedisUnavailableCachingIntegrationTest`, `FailOpenCacheTest`,
`CachedValueSerializationTest`). Approved with the recommended answer to every open
question below. Two decisions changed during implementation, both recorded in place:
cached values are the domain objects themselves (not dedicated records), and fail-open is
a cache decorator, not a `CacheErrorHandler`. Platform-level,
cross-cutting like [07-audit-trail](../07-audit-trail/requirements.md) and
[14-webhooks](../14-webhooks/requirements.md): no new endpoint, no new persisted entity, no
migration, no change to any HTTP contract.

Scope: today the Redis cache is wired (`spring-boot-starter-cache` in `infrastructure`,
`spring.cache.type: redis`, `@EnableCaching` on `InventoryApiApplication`) but nothing uses
it. If someone added a `@Cacheable` today, its entries would never expire, which breaks
[data-and-infra.md](../../conventions/data-and-infra.md)'s Redis rules. This feature adds
the first caches and the configuration they need to follow those rules.

## Decision - cache by-id lookups of three slow-changing entities, and nothing else

**What is cached:** `findById` on `ProductRepository`, `WarehouseRepository` and
`CategoryRepository`. These are the lookups repeated on almost every write in the system,
not only on their own `GET /{id}` endpoints:

| Lookup | Callers outside its own CRUD service |
|---|---|
| `ProductRepository.findById` | `StockMovementRecordingService` (every receipt/shipment/transfer/adjustment), `PurchaseOrderService`, `SalesOrderService`, `CycleCountService`, `ReorderThresholdService`, `ReportingService.inventoryValuation` (once per row, up to a full page) |
| `WarehouseRepository.findById` | `ActiveWarehouseValidator` (every write against a warehouse), `ReorderThresholdService` |
| `CategoryRepository.findById` | `ProductService.create`/`update` (category existence), `CategoryService` (parent existence) |

All three are reference data: created rarely, updated rarely, never deleted. The only
writer of each is its repository adapter's `save` (verified - no delete, no bulk
`UPDATE`, no `@Modifying` query touches `products`/`warehouses`/`categories`). So
"evict on save" covers every way the cached value can change.

**What is deliberately not cached:**

- `StockLevel`, stock movements, stock alerts, purchase/sales orders, cycle counts - they
  change with every movement. A stale read there is a business bug (wrong availability,
  double reservation), not a performance trade-off.
- Paginated/filtered lists (`findAll`) of any resource - the number of filter and page
  combinations is unbounded, and any single write would have to evict all of them.
- The three reports in [10-reporting](../10-reporting/requirements.md) - they are computed
  from stock data that changes constantly. Their real cost problem (a per-row query in
  `inventoryValuation`/`stockAging`, and `movementSummary` loading every matching
  movement into memory) is fixed with SQL aggregation, not by caching the result - see
  "Out of scope".
- `CategoryRepository.findByName` (used by CSV import) - a name-keyed entry would also
  need evicting under the *old* name on rename; not worth it for an endpoint capped at
  1000 rows per request.
- Users, roles, JWT validation - authentication is already stateless (see
  [docs/security.md](../../security.md)); caching a user's role would delay a role change
  or deactivation.

**Where:** on the three repository adapters in `infrastructure`
(`ProductRepositoryAdapter`, etc.), not on the `application` services. Reasons:

- [architecture.md](../../conventions/architecture.md) assigns "Redis-backed caches" to
  `infrastructure`; `application` and `domain` stay unaware that a cache exists.
- Every caller in the table above benefits, not only the `GET /{id}` endpoint.
- No self-invocation trap: `ProductService.update` calls its own `get(id)` internally,
  which a service-level `@Cacheable` would silently skip (Spring proxies don't intercept
  self-calls).

### Consistency rules

1. **Eviction happens after commit, never before.** The cache manager is built
   transaction-aware, so the `@CacheEvict` on `save` and any `@Cacheable` put made inside a
   transaction run only once that transaction commits. Without this, a concurrent reader
   could re-cache the old row between the evict and the commit. A rolled-back
   transaction neither evicts nor caches anything.
2. **Evict on save, not put.** After a write, the entry is removed and the next read
   reloads it from Postgres. Values nobody reads are never cached.
3. **Misses are never cached** (null values disabled). Otherwise every
   `GET /products/{random-uuid}` would write a Redis key - an easy way to fill Redis from
   outside.
4. **Read-your-own-write limitation, documented on purpose:** because eviction is deferred
   to commit, a `findById` made *after* `save` in the same transaction returns the
   pre-write value. No current code path does that (verified: every `update` reads
   `existing` before saving, and nothing re-reads afterwards). The rule from here on is to
   use the value `save` returns, never a fresh `findById` of the same id in the same
   transaction.
5. **Remaining staleness window:** reader A misses the cache and reads the row from
   Postgres just before writer B commits. B evicts after commit, then A writes its
   (now old) value into the cache. That entry stays wrong until its TTL expires. The race
   needs a cache miss and a concurrent write on the same id within milliseconds; the TTL
   is the upper bound. See review decision 1 for what this means for `active` flags.

### Cache configuration

- **Cache names are constants**, in one `CacheNames` class in `infrastructure`
  (`PRODUCTS_BY_ID`, `WAREHOUSES_BY_ID`, `CATEGORIES_BY_ID`), never a string literal at the
  annotation.
- **Every cache has an explicit TTL**, configured per cache under
  `app.cache.ttl.{products, warehouses, categories}` (`Duration`, bound to a
  `@ConfigurationProperties` record), default **10 minutes** each (see review decision 2).
- **Creating a cache on the fly is disabled** - only the three declared caches exist. A
  `@Cacheable` with an undeclared name fails instead of silently getting a cache with no
  TTL, which keeps the "every entry has an explicit TTL" rule enforced by code, not only by
  review.
- **Key prefix `inventory:cache:v1:`** (constant) - the local Redis is shared with other
  projects (see [data-and-infra.md](../../conventions/data-and-infra.md)'s local dev stack),
  and the `v1` segment is bumped whenever a cached value's shape changes, so a deploy never
  reads entries written in an older format.
- **JSON values, not JDK serialization**, one typed `JacksonJsonRedisSerializer` per cache
  (Jackson 3, `tools.jackson` - see [docs/SOURCES.md](../../SOURCES.md)), never a generic
  serializer that writes class names into the payload. The cached value is the domain
  object itself (`Product`/`Warehouse`/`Category`): Lombok `@Value` classes deserialize
  through their all-args constructor with no annotation, because the build compiles with
  `-parameters` (`spring-boot-starter-parent`). *Changed during implementation:* the first
  draft cached dedicated `infrastructure` records, which meant three more copies of the
  same field mapping per entity. What those records were meant to protect against - a
  domain class changing shape between deploys - is handled by the mapper instead: it is
  strict (`FAIL_ON_UNKNOWN_PROPERTIES`, `FAIL_ON_MISSING_CREATOR_PROPERTIES`), so an entry
  written with a different shape fails to deserialize and is reloaded from Postgres (see
  below), never read back with a silently defaulted field. `CachedValueSerializationTest`
  pins both the round-trip and the rejection.

### Redis unavailable - fails open, like the rate limiters

A cache is a performance optimization; Redis being down must never take down a request
that Postgres can answer. `FailOpenCache` wraps every Redis cache and catches
`DataAccessException` (connection/timeout) and `SerializationException` (unreadable value):

- **Get error:** log `WARN` with cache name and key, treat as a miss - the request
  continues against Postgres.
- **Put error:** log `WARN`, continue (the value just isn't cached).
- **Evict error:** log `ERROR` - Postgres already committed the new value, so the old entry
  may be served until its TTL expires; this needs attention. Continue (the transaction is
  already committed and cannot be undone).

*Changed during implementation:* the draft used a `CacheErrorHandler`. That handler only
sees calls made by the caching aspect itself, and with a transaction-aware manager the put
and evict calls run later, in an after-commit callback (verified in
`TransactionAwareCacheDecorator`'s source, `spring-context-support` 7.0.9). A Redis
failure there would never reach the handler and would turn an already-committed write into
a `500`. So the stack is built by hand in `RedisCacheConfig`:
`TransactionAwareCacheManagerProxy` → `FailOpenCacheManager` → `RedisCacheManager`. Spring
Boot's own Redis cache manager backs off when this bean exists; `RedisCacheConfig` is only
active with `spring.cache.type=redis`.

The existing `spring.data.redis.timeout: 250ms` keeps each failed call short. With Redis
down, a request that makes several lookups pays up to 250 ms per lookup; a circuit breaker
that skips Redis entirely after repeated failures is out of scope (see below).

## Acceptance criteria

1. Two consecutive `GET /api/v1/products/{id}` for the same existing product within the
   TTL produce exactly one `SELECT` against `products` (same for warehouses and
   categories).
2. Recording a stock movement for a product whose entry is already cached does not query
   `products` for that lookup.
3. After `PATCH /api/v1/products/{id}` commits, the next `GET` of that id returns the
   updated values - never the cached pre-update ones. Same for warehouses and categories.
4. Deactivating a warehouse (`PATCH` with `active: false`) and then recording a movement
   against it is rejected immediately with the existing `WarehouseInactiveException`
   response - not only after the TTL. Same for a deactivated product and
   `ProductInactiveException`.
5. A write whose transaction rolls back does not evict the entry and does not cache the
   value it read.
6. A `GET` of an id that does not exist returns `404` and leaves no key in Redis.
7. Every key the three caches write has the `inventory:cache:v1:` prefix and a Redis TTL
   greater than 0 and no greater than the configured value for its cache.
8. With Redis unreachable, `GET /api/v1/products/{id}` and a stock movement both still
   succeed, a `WARN` is logged, and no `5xx` is returned.
9. Every response body is byte-for-byte the same with caching enabled or disabled - this
   feature changes no HTTP contract.

## Testing

Per [testing.md](../../conventions/testing.md):

- **`ReferenceDataCachingIntegrationTest`** covers criteria 1-7 and 9 against the shared
  `local-infrastructure` Redis - the same exception the two rate-limit tests already use.
  Postgres reads are counted with Hibernate statistics (entity load count). It clears the
  three caches before each test, next to `TruncateBusinessTablesExtension` wiping the tables.
- **`RedisUnavailableCachingIntegrationTest`** covers criterion 8 by pointing
  `spring.data.redis.port` at a port nothing listens on.
- **Every other `@SpringBootTest`** already sets `spring.cache.type=simple`, so
  `RedisCacheConfig` is inactive there and Spring Boot's in-memory manager serves the same
  annotations - their results never depend on what a dev run left in Redis.
- **Unit tests:** `FailOpenCacheTest` (each failure is swallowed and logged at the level
  stated above; a loader still answers when Redis is down) and
  `CachedValueSerializationTest` (round-trip and strict rejection).

## Docs updated with this feature

- [data-and-infra.md](../../conventions/data-and-infra.md), Redis section.
- [testing.md](../../conventions/testing.md): the cache integration test as a second
  shared-Redis exception.
- [docs/SOURCES.md](../../SOURCES.md): the Spring Data Redis 4 / Spring 7 cache classes
  used, each confirmed against the resolved jar.
- `.env.example` and `application.yml`: `CACHE_TTL_*`.
- `AGENTS.md`: this spec in the "Implemented so far" list.

## Review decisions (all resolved with the recommendation)

1. **Is a stale `active` flag acceptable for up to one TTL in the rare race above?**
   **Accepted.**
   `ActiveWarehouseValidator` and `StockMovementRecordingService` read `active` through
   the cached lookup. Normal deactivation is immediate (criterion 4); only the concurrent
   race from consistency rule 5 can leave a deactivated product/warehouse cached as active
   until its TTL expires. **Recommendation: accept it**, bounded by the TTL - deactivation
   is an administrative action, not a safety interlock. The alternative is to read
   `active` from Postgres on write paths, which removes most of the benefit of this
   feature.
2. **TTL values: 10 minutes for all three** (overridable per cache via `CACHE_TTL_*`).
   With eviction on every write, the TTL only bounds the race window and limits Redis
   memory - it is not the normal freshness delay.
3. **Real Redis in tests: the shared local Redis** (rate-limit precedent), not a
   Testcontainers `GenericContainer`.

## Out of scope for this feature

- Caching anything not listed under "What is cached" - each new cache is a new decision
  against the "deliberately not cached" criteria above.
- Fixing the per-row queries in `inventoryValuation`/`stockAging` and the in-memory
  aggregation in `movementSummary` (`10-reporting`) - SQL aggregation, a separate change.
- A circuit breaker for Redis - the 250 ms timeout plus fail-open is enough for now.
- Cache metrics/hit-rate endpoints - the WARN/ERROR logs are the only signal, consistent
  with the project's current observability stance.
- Namespacing the existing rate-limit keys (`rate-limit:*`) with the same `inventory:`
  prefix - worth doing, but it changes `RateLimitKeys`, which this feature doesn't touch.
- Pre-warming the caches at startup.
