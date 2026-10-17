package com.jm.inventory.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires {@code MANAGER} or {@code ADMIN}. See {@link RequireStaff}'s Javadoc for why the
 * role names are repeated as literals here rather than derived from {@link com.jm.inventory.domain.user.Role}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public @interface RequireManager {
}
