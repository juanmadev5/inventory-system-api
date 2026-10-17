package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.ConflictException;

public class CategoryNameAlreadyExistsException extends ConflictException {

    public CategoryNameAlreadyExistsException(String name) {
        super("A category named '" + name + "' already exists");
    }

    @Override
    public String code() {
        return "CATEGORY_NAME_ALREADY_EXISTS";
    }
}
