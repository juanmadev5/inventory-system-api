package com.jm.inventory.infrastructure.platform;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A fixed-window counter: {@code INCR} the key, set its TTL the first time it's created.
 * Deliberately simple (no sliding window, no token-bucket smoothing) - good enough for
 * "block obvious abuse," not meant to be precise.
 *
 * <p><b>Fails open.</b> If Redis is unreachable, this allows the request rather than
 * blocking it - a rate limiter is a secondary defense; its own backing store being down
 * must never take down the primary feature it's protecting. See {@code spring.data.redis.timeout}
 * in {@code application.yml} for the short timeout that keeps this failure fast rather than
 * a multi-second hang per request.
 */
@Component
public class RedisRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        try {
            Long countInWindow = redisTemplate.opsForValue().increment(key);
            if (countInWindow != null && countInWindow == 1L) {
                redisTemplate.expire(key, window);
            }
            return countInWindow == null || countInWindow <= maxRequests;
        } catch (DataAccessException redisUnavailable) {
            LOG.warn("Rate limiter could not reach Redis for key '{}' - failing open", key, redisUnavailable);
            return true;
        }
    }
}
