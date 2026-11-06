package com.jm.inventory.infrastructure.platform.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class FailOpenCacheManager implements CacheManager {

    private final CacheManager target;
    private final Map<String, Cache> decoratedCachesByName = new ConcurrentHashMap<>();

    FailOpenCacheManager(CacheManager target) {
        this.target = target;
    }

    @Override
    public Cache getCache(String name) {
        Cache targetCache = target.getCache(name);
        if (targetCache == null) {
            return null;
        }
        return decoratedCachesByName.computeIfAbsent(name, cacheName -> new FailOpenCache(targetCache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return target.getCacheNames();
    }
}
