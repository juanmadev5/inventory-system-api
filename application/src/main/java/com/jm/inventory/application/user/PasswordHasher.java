package com.jm.inventory.application.user;

/**
 * Application-owned abstraction for password hashing - {@code infrastructure} provides the
 * real (BCrypt) implementation, same dependency-inversion discipline as every repository
 * interface in this project.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
