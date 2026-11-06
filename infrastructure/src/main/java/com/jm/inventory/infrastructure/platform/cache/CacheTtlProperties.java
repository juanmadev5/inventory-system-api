package com.jm.inventory.infrastructure.platform.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.cache.ttl.*}. Every entry is evicted on write anyway; the TTL only
 * bounds how long a rare read/write race can leave an outdated entry behind (see
 * docs/specs/15-reference-data-caching/requirements.md, consistency rule 5).
 */
@ConfigurationProperties(prefix = "app.cache.ttl")
public record CacheTtlProperties(Duration products, Duration warehouses, Duration categories) {

    public CacheTtlProperties {
        requirePositive("products", products);
        requirePositive("warehouses", warehouses);
        requirePositive("categories", categories);
    }

    private static void requirePositive(String name, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.cache.ttl." + name + " must be a positive duration, got " + ttl);
        }
    }
}
