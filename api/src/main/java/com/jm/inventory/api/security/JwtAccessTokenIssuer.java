package com.jm.inventory.api.security;

import com.jm.inventory.application.user.AccessTokenIssuer;
import com.jm.inventory.domain.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * This application's own token issuance - no external authorization server. See
 * {@link RoleJwtAuthenticationConverter} for the matching claim read on the way back in.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";

    private final JwtProperties jwtProperties;
    private final NimbusJwtEncoder jwtEncoder;

    public JwtAccessTokenIssuer(JwtProperties jwtProperties, JwtSigningKey jwtSigningKey) {
        this.jwtProperties = jwtProperties;
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey.get()));
    }

    @Override
    public IssuedAccessToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.expirationMinutes() * 60);

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, jwtProperties.expirationMinutes() * 60);
    }
}
