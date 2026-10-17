package com.jm.inventory.api.user;

import com.jm.inventory.application.user.AccessTokenIssuer.IssuedAccessToken;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static LoginResponse from(IssuedAccessToken issuedAccessToken) {
        return new LoginResponse(issuedAccessToken.token(), "Bearer", issuedAccessToken.expiresInSeconds());
    }
}
