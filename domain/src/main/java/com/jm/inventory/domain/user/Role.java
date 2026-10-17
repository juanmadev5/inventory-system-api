package com.jm.inventory.domain.user;

/**
 * The three roles a user can hold, strictly additive ({@code ADMIN} can do everything
 * {@code MANAGER} can, which can do everything {@code STAFF} can) - see
 * docs/specs/06-roles-and-permissions/requirements.md. Carried in the access token issued by
 * this application's own {@code POST /api/v1/auth/login}, as a single {@code role} claim
 * (never a list - a user holds exactly one role at a time).
 */
public enum Role {
    STAFF,
    MANAGER,
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /** The Spring Security {@code GrantedAuthority} name this role maps to. */
    public String authorityName() {
        return AUTHORITY_PREFIX + name();
    }
}
