package com.jm.inventory.domain.common;

/**
 * Base for every "this request conflicts with existing state" domain exception (e.g. a
 * uniqueness violation) - mapped to {@code 409} in one place by {@code GlobalExceptionHandler}.
 */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }

    public abstract String code();
}
