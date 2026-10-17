package com.jm.inventory.domain.common;

/**
 * Base for "the request is well-formed but references an id that doesn't exist" - e.g. a
 * product created with an unknown {@code categoryId}. Mapped to {@code 422} /
 * {@code VALIDATION_FAILED} by {@code GlobalExceptionHandler}, with {@link #field()} filling
 * the same field-level error shape Bean Validation failures use, so a client can't tell the
 * two apart by response shape.
 */
public abstract class InvalidReferenceException extends RuntimeException {

    private final String field;

    protected InvalidReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
