package com.jm.inventory.api.user;

import com.jm.inventory.domain.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(@NotNull Role role) {
}
