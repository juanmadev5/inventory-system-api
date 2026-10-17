package com.jm.inventory.application.common;

/**
 * The field-level error shape used by both Bean Validation failures and this
 * application's own {@code InvalidReferenceException} hierarchy, per
 * docs/conventions/api-design.md - a client branches on {@code code}, not on which of the
 * two produced a given field error.
 */
public record FieldError(String field, String message) {
}
