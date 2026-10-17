package com.jm.inventory.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.security.bootstrap-admin.*}. Both blank by default - a blank value
 * means "do not bootstrap anyone," checked by {@link AdminBootstrapRunner}, never a silent
 * fallback to a hardcoded credential.
 */
@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password) {
}
