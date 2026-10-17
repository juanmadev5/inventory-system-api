package com.jm.inventory.application.user;

import com.jm.inventory.domain.user.User;

/**
 * Application-owned abstraction for "turn an authenticated user into a bearer access
 * token". The real implementation (JWT signing) lives in {@code api}, since Spring
 * Security/JWT concerns belong there - see {@link com.jm.inventory.application.audit.CurrentActor}
 * for the mirrored pattern on the read side.
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user);

    record IssuedAccessToken(String token, long expiresInSeconds) {
    }
}
