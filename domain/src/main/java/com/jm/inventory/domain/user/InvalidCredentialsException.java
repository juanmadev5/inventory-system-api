package com.jm.inventory.domain.user;

/**
 * Thrown for any login failure - unknown email, wrong password, or an inactive account -
 * deliberately without distinguishing which, so {@code POST /api/v1/auth/login} never tells
 * a caller whether an email exists in this system.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
