package com.jm.inventory.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.security.jwt.*} - see {@code application.yml}. This application is
 * its own identity provider: it signs and validates its own access tokens with one shared
 * HMAC secret, never delegating to an external authorization server.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, long expirationMinutes) {
}
