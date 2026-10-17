package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException(UUID id) {
        super("Product " + id + " not found");
    }

    @Override
    public String code() {
        return "PRODUCT_NOT_FOUND";
    }
}
