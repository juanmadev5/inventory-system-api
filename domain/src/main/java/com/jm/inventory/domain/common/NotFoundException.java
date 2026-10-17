package com.jm.inventory.domain.common;

/**
 * Base for every "this id doesn't exist" domain exception - {@code GlobalExceptionHandler}
 * maps the whole hierarchy to {@code 404} in one place.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }

    public abstract String code();
}
