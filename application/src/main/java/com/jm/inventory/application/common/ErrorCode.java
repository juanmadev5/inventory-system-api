package com.jm.inventory.application.common;

/**
 * Stable, machine-readable error codes surfaced in every {@code ProblemDetail} response.
 * Grows as each feature adds its own domain-specific codes (e.g. {@code PRODUCT_NOT_FOUND})
 * - this enum only holds the cross-cutting ones that don't belong to a single feature.
 * Always transmitted by name ({@link Enum#name()}), never by ordinal.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    ENDPOINT_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
