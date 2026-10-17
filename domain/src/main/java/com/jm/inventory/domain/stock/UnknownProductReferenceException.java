package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class UnknownProductReferenceException extends InvalidReferenceException {

    public UnknownProductReferenceException(UUID productId) {
        super("productId", "Product " + productId + " does not exist");
    }
}
