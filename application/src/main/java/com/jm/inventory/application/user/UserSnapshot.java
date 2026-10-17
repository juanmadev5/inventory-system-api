package com.jm.inventory.application.user;

import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import java.util.UUID;

/** What gets audited for a user write - never {@code passwordHash}. */
public record UserSnapshot(UUID id, String email, Role role, boolean active) {

    public static UserSnapshot from(User user) {
        return new UserSnapshot(user.getId(), user.getEmail(), user.getRole(), user.isActive());
    }
}
