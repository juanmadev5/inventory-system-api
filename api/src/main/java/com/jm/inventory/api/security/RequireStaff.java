package com.jm.inventory.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires {@code STAFF}, {@code MANAGER}, or {@code ADMIN} - i.e. any recognized role.
 * See docs/specs/06-roles-and-permissions/requirements.md. The role names are repeated as SpEL
 * string literals here (and in {@link RequireManager}/{@link RequireAdmin}) because
 * {@code @PreAuthorize} requires a compile-time literal; this annotation is the one place that
 * literal lives for this permission level, never copy-pasted onto a controller method directly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
public @interface RequireStaff {
}
