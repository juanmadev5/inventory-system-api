package com.jm.inventory.domain.user;

import com.jm.inventory.domain.common.ConflictException;

public class UserEmailAlreadyExistsException extends ConflictException {

    public UserEmailAlreadyExistsException(String email) {
        super("A user with email '" + email + "' already exists");
    }

    @Override
    public String code() {
        return "USER_EMAIL_ALREADY_EXISTS";
    }
}
