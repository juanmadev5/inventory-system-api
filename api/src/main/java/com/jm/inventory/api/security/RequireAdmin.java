package com.jm.inventory.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires {@code ADMIN} only. See {@link RequireStaff}'s Javadoc for why the role name is
 * repeated as a literal here rather than derived from {@link com.jm.inventory.domain.user.Role}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasRole('ADMIN')")
public @interface RequireAdmin {
}
