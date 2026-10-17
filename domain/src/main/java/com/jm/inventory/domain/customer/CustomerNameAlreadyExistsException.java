package com.jm.inventory.domain.customer;

import com.jm.inventory.domain.common.ConflictException;

public class CustomerNameAlreadyExistsException extends ConflictException {

    public CustomerNameAlreadyExistsException(String name) {
        super("A customer named '" + name + "' already exists");
    }

    @Override
    public String code() {
        return "CUSTOMER_NAME_ALREADY_EXISTS";
    }
}
