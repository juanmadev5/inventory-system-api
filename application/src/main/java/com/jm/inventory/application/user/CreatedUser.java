package com.jm.inventory.application.user;

import com.jm.inventory.domain.user.User;

/**
 * {@code temporaryPassword} is returned exactly once, here - never retrievable again, never
 * logged, never stored. See docs/specs/11-user-management/requirements.md.
 */
public record CreatedUser(User user, String temporaryPassword) {
}
