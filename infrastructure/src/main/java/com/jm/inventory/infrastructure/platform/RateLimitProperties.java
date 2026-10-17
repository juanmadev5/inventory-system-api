package com.jm.inventory.infrastructure.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.rate-limit.*}. {@code login} protects {@code POST
 * /api/v1/auth/login} only (keyed by caller IP, see {@code LoginRateLimitFilter}'s own
 * Javadoc for why - brute-forcing a password is the threat model there). {@code apiWide} is
 * the general limiter: every authenticated business endpoint, keyed by the caller's own
 * user id (see {@code ApiRateLimitFilter}) - a noisy or misbehaving account's traffic never
 * starves another's, and the limiter itself is Redis-only, so it never adds pressure to the
 * Postgres connection pool.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Limit login, Limit apiWide) {

    public record Limit(int maxRequests, int windowSeconds) {
    }
}
