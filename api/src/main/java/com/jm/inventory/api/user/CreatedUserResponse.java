package com.jm.inventory.api.user;

import com.jm.inventory.application.user.CreatedUser;

/** {@code temporaryPassword} appears here, once - see {@code CreatedUser}'s Javadoc. */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {

    public static CreatedUserResponse from(CreatedUser createdUser) {
        return new CreatedUserResponse(UserResponse.from(createdUser.user()), createdUser.temporaryPassword());
    }
}
