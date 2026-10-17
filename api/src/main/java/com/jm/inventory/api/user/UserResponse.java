package com.jm.inventory.api.user;

import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, boolean active, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
