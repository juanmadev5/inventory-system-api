package com.jm.inventory.infrastructure.platform.cache;

import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.warehouse.Warehouse;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces Spring Boot's auto-configured Redis cache manager (which backs off once this bean
 * exists) because the cache stack needs one layer Boot cannot add: {@link FailOpenCache}
 * between Redis and the transaction-aware decorator. Only active when
 * {@code spring.cache.type=redis}, so tests running with {@code simple} still get Boot's
 * in-memory manager. See docs/specs/15-reference-data-caching/requirements.md.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RedisCacheConfig.CACHE_TYPE_PROPERTY, havingValue = RedisCacheConfig.REDIS_CACHE_TYPE)
public class RedisCacheConfig {

    static final String CACHE_TYPE_PROPERTY = "spring.cache.type";
    static final String REDIS_CACHE_TYPE = "redis";

    /**
     * Strict on both sides so a cached value whose shape no longer matches its class (a
     * field added or removed in a later deploy) fails to deserialize - which
     * {@link FailOpenCache} turns into a miss and a reload - instead of silently coming
     * back with a defaulted field (a new {@code boolean} reading as {@code false}).
     */
    static final JsonMapper STRICT_JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .build();

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, CacheTtlProperties ttl) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CacheNames.KEY_PREFIX)
                .disableCachingNullValues();

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(CacheNames.PRODUCTS_BY_ID, typed(defaults, Product.class, ttl.products()))
                .withCacheConfiguration(CacheNames.WAREHOUSES_BY_ID, typed(defaults, Warehouse.class, ttl.warehouses()))
                .withCacheConfiguration(CacheNames.CATEGORIES_BY_ID, typed(defaults, Category.class, ttl.categories()))
                .disableCreateOnMissingCache()
                .build();
        // Not a bean itself, so Spring never calls this for us - it loads the declared caches.
        redisCacheManager.afterPropertiesSet();

        return new TransactionAwareCacheManagerProxy(new FailOpenCacheManager(redisCacheManager));
    }

    private static <T> RedisCacheConfiguration typed(RedisCacheConfiguration defaults, Class<T> valueType, Duration ttl) {
        return defaults
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(STRICT_JSON_MAPPER, valueType)));
    }
}
