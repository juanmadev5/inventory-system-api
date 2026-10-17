package com.jm.inventory.domain.user;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID id) {
        super("User " + id + " not found");
    }

    @Override
    public String code() {
        return "USER_NOT_FOUND";
    }
}
