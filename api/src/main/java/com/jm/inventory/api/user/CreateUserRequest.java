package com.jm.inventory.api.user;

import com.jm.inventory.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(@NotBlank @Email String email, @NotNull Role role) {
}
