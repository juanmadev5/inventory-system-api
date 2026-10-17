package com.jm.inventory.infrastructure.user;

import com.jm.inventory.application.user.TemporaryPasswordGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Cryptographically random, never a predictable pattern - no complexity policy to satisfy
 * beyond length, since the user is expected to change it after first login.
 */
@Component
public class SecureTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 20;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
