package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class ProductInactiveException extends ConflictException {

    public ProductInactiveException(UUID productId) {
        super("Product " + productId + " is inactive and cannot receive new stock");
    }

    @Override
    public String code() {
        return "PRODUCT_INACTIVE";
    }
}
