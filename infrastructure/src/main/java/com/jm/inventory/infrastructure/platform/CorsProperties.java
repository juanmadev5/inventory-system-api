package com.jm.inventory.infrastructure.platform;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.cors.allowed-origins} - a comma-separated list, empty by default.
 * CORS is wired into {@code SecurityConfig}'s filter chain regardless (the
 * mechanism is always "on"), but with zero allowed origins nothing is actually permitted
 * cross-origin until a real frontend URL is configured via {@code CORS_ALLOWED_ORIGINS} -
 * "prepared for CORS," not "CORS open."
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
