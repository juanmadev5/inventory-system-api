package com.jm.inventory.application.common;

/**
 * Stable, machine-readable error codes surfaced in every {@code ProblemDetail} response.
 * Grows as each feature adds its own domain-specific codes (e.g. {@code PRODUCT_NOT_FOUND})
 * - this enum only holds the cross-cutting ones that don't belong to a single feature.
 * Always transmitted by name ({@link Enum#name()}), never by ordinal.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    UNAUTHENTICATED,
    FORBIDDEN,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
