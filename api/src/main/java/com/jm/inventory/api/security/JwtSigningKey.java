package com.jm.inventory.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Derives the actual HMAC-SHA256 signing/verification key from the configured
 * {@code app.security.jwt.secret} by hashing it with SHA-256 - guarantees a full 256-bit key
 * regardless of the configured secret's own length, rather than failing at startup (or,
 * worse, silently using a weak key) if someone configures a short one. Shared by
 * {@link JwtAccessTokenIssuer} (signing) and the {@code JwtDecoder} bean in
 * {@link SecurityConfig} (verification) - both must use the exact same key.
 */
@Component
public class JwtSigningKey {

    private final SecretKey secretKey;

    public JwtSigningKey(JwtProperties jwtProperties) {
        this.secretKey = new SecretKeySpec(sha256(jwtProperties.secret()), "HmacSHA256");
    }

    public SecretKey get() {
        return secretKey;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is always available on a standard JVM", impossible);
        }
    }
}
