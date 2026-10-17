package com.jm.inventory.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** A pure unit test (no Spring context, no real token decoding). */
class RoleJwtAuthenticationConverterTest {

    private final RoleJwtAuthenticationConverter converter = new RoleJwtAuthenticationConverter();

    @Test
    void given_aTokenWithARecognizedRole_when_converting_then_grantsTheMatchingAuthority() {
        // Given
        Jwt jwt = jwtWithRole("MANAGER");

        // When
        AbstractAuthenticationToken authentication = converter.convert(jwt);

        // Then
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    void given_aTokenWithAnUnrecognizedRoleValue_when_converting_then_grantsNoAuthority() {
        // Given
        Jwt jwt = jwtWithRole("SUPERUSER");

        // When
        AbstractAuthenticationToken authentication = converter.convert(jwt);

        // Then
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void given_aTokenWithNoRoleClaimAtAll_when_converting_then_grantsNoAuthority() {
        // Given
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "someone")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        // When
        AbstractAuthenticationToken authentication = converter.convert(jwt);

        // Then
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwtWithRole(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "someone")
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
