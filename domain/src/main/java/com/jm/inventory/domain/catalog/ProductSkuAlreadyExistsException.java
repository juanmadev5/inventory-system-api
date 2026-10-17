package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.ConflictException;

public class ProductSkuAlreadyExistsException extends ConflictException {

    public ProductSkuAlreadyExistsException(String sku) {
        super("A product with SKU '" + sku + "' already exists");
    }

    @Override
    public String code() {
        return "PRODUCT_SKU_ALREADY_EXISTS";
    }
}
