package com.jm.inventory.infrastructure.platform.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Makes every Redis failure behave like a cache miss or a skipped write, never a failed
 * request - Postgres can always answer instead. Sits <i>under</i> Spring's
 * transaction-aware decorator on purpose: that decorator runs puts and evictions in an
 * after-commit callback, outside the reach of a {@code CacheErrorHandler}, where an
 * exception would turn an already-committed write into a {@code 500}.
 */
class FailOpenCache implements Cache {

    private static final Logger LOG = LoggerFactory.getLogger(FailOpenCache.class);

    private final Cache target;

    FailOpenCache(Cache target) {
        this.target = target;
    }

    @Override
    public String getName() {
        return target.getName();
    }

    @Override
    public Object getNativeCache() {
        return target.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return target.get(key);
        } catch (DataAccessException | SerializationException cacheFailure) {
            logReadFailure(key, cacheFailure);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return target.get(key, type);
        } catch (DataAccessException | SerializationException cacheFailure) {
            logReadFailure(key, cacheFailure);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return target.get(key, valueLoader);
        } catch (DataAccessException | SerializationException cacheFailure) {
            logReadFailure(key, cacheFailure);
            try {
                return valueLoader.call();
            } catch (Exception loaderFailure) {
                throw new ValueRetrievalException(key, valueLoader, loaderFailure);
            }
        }
    }

    @Override
    public CompletableFuture<?> retrieve(Object key) {
        return target.retrieve(key);
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        return target.retrieve(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        try {
            target.put(key, value);
        } catch (DataAccessException | SerializationException cacheFailure) {
            LOG.warn("Cache '{}' could not store key '{}' - continuing uncached", getName(), key, cacheFailure);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return target.putIfAbsent(key, value);
        } catch (DataAccessException | SerializationException cacheFailure) {
            LOG.warn("Cache '{}' could not store key '{}' - continuing uncached", getName(), key, cacheFailure);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            target.evict(key);
        } catch (DataAccessException cacheFailure) {
            logEvictFailure(key, cacheFailure);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            return target.evictIfPresent(key);
        } catch (DataAccessException cacheFailure) {
            logEvictFailure(key, cacheFailure);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            target.clear();
        } catch (DataAccessException cacheFailure) {
            LOG.error("Cache '{}' could not be cleared - entries may be outdated until their TTL expires",
                    getName(), cacheFailure);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return target.invalidate();
        } catch (DataAccessException cacheFailure) {
            LOG.error("Cache '{}' could not be invalidated - entries may be outdated until their TTL expires",
                    getName(), cacheFailure);
            return false;
        }
    }

    private void logReadFailure(Object key, RuntimeException cacheFailure) {
        LOG.warn("Cache '{}' could not read key '{}' - treating as a miss", getName(), key, cacheFailure);
    }

    private void logEvictFailure(Object key, RuntimeException cacheFailure) {
        LOG.error("Cache '{}' could not evict key '{}' - the entry may be outdated until its TTL expires",
                getName(), key, cacheFailure);
    }
}
