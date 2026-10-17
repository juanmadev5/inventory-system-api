package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException(UUID id) {
        super("Category " + id + " not found");
    }

    @Override
    public String code() {
        return "CATEGORY_NOT_FOUND";
    }
}
