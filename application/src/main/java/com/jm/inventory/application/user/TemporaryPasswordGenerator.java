package com.jm.inventory.application.user;

/**
 * Application-owned abstraction for generating the one-time temporary password a newly
 * created user's {@code ADMIN} relays out of band - see
 * docs/specs/11-user-management/requirements.md. {@code infrastructure} provides the real
 * (cryptographically random) implementation.
 */
public interface TemporaryPasswordGenerator {

    String generate();
}
