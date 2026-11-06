package com.jm.inventory.infrastructure.platform.cache;

/**
 * The only caches that exist - {@link RedisCacheConfig} declares exactly these and disables
 * on-the-fly creation, so a {@code @Cacheable} naming anything else fails instead of getting
 * a cache with no TTL. See docs/specs/15-reference-data-caching/requirements.md.
 */
public final class CacheNames {

    public static final String PRODUCTS_BY_ID = "products-by-id";
    public static final String WAREHOUSES_BY_ID = "warehouses-by-id";
    public static final String CATEGORIES_BY_ID = "categories-by-id";

    /**
     * Bump the version segment whenever a cached value's JSON shape changes incompatibly,
     * so a new deploy never reads entries written by the previous one.
     */
    public static final String KEY_PREFIX = "inventory:cache:v1:";

    private CacheNames() {
    }
}
