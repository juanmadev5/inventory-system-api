package com.jm.inventory.api.security;

import com.jm.inventory.application.audit.CurrentActor;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated JWT's {@code sub}/{@code email} claims - see
 * docs/specs/07-audit-trail/requirements.md for the one legitimate case this returns
 * {@code null} for both (a direct application-layer call with no HTTP request behind it).
 */
@Component
public class JwtCurrentActor implements CurrentActor {

    private static final String EMAIL_CLAIM = "email";

    @Override
    public String actorId() {
        return currentJwtAuthentication().map(authentication -> authentication.getToken().getSubject()).orElse(null);
    }

    @Override
    public String actorUsername() {
        return currentJwtAuthentication()
                .map(authentication -> authentication.getToken().getClaimAsString(EMAIL_CLAIM))
                .orElse(null);
    }

    private Optional<JwtAuthenticationToken> currentJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwtAuthenticationToken
                ? Optional.of(jwtAuthenticationToken)
                : Optional.empty();
    }
}
