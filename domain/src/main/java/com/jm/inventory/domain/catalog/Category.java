package com.jm.inventory.domain.catalog;

import java.util.UUID;
import lombok.Value;

@Value
public class Category {
    UUID id;
    String name;
    UUID parentCategoryId;
}
