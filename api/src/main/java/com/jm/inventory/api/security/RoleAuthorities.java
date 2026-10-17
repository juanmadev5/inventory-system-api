package com.jm.inventory.api.security;

import com.jm.inventory.domain.user.Role;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The one place {@link Role} (a pure domain enum, no Spring Security on its classpath) is
 * translated into an {@link Authentication} check - used only by
 * {@code StockMovementController} for its one request-body-dependent authorization rule,
 * which {@code @PreAuthorize} cannot express declaratively. Every other authorization
 * decision uses the {@link RequireStaff}/{@link RequireManager}/{@link RequireAdmin}
 * composed annotations instead.
 */
public final class RoleAuthorities {

    private RoleAuthorities() {
    }

    /** True if {@code authentication} holds {@code role} or a higher one. */
    public static boolean hasAtLeast(Role role, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(RoleAuthorities::roleFromAuthority)
                .flatMap(Optional::stream)
                .anyMatch(grantedRole -> grantedRole.ordinal() >= role.ordinal());
    }

    private static Optional<Role> roleFromAuthority(String authority) {
        return Arrays.stream(Role.values()).filter(role -> role.authorityName().equals(authority)).findFirst();
    }
}
