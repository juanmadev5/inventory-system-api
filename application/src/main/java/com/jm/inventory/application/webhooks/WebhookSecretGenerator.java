package com.jm.inventory.application.webhooks;

import java.security.SecureRandom;

/**
 * Pure JDK, no Spring/HTTP dependency needed - same reasoning as not extracting an
 * interface for a single, stateless implementation elsewhere in this module.
 */
final class WebhookSecretGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSecretGenerator() {
    }

    static String generate() {
        StringBuilder secret = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            secret.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return secret.toString();
    }
}
