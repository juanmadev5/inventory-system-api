package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class UnknownCategoryReferenceException extends InvalidReferenceException {

    public UnknownCategoryReferenceException(UUID categoryId) {
        super("categoryId", "Category " + categoryId + " does not exist");
    }
}
