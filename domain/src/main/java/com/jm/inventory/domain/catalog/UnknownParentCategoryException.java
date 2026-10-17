package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class UnknownParentCategoryException extends InvalidReferenceException {

    public UnknownParentCategoryException(UUID parentCategoryId) {
        super("parentCategoryId", "Category " + parentCategoryId + " does not exist");
    }
}
