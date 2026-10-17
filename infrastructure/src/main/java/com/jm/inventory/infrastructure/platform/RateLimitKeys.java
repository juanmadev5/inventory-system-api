package com.jm.inventory.infrastructure.platform;

public final class RateLimitKeys {

    public static final String LOGIN_PREFIX = "rate-limit:login:";
    public static final String API_WIDE_PREFIX = "rate-limit:api-wide:";

    private RateLimitKeys() {
    }
}
