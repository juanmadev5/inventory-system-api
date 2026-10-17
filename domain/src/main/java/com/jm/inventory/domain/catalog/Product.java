package com.jm.inventory.domain.catalog;

import java.util.UUID;
import lombok.Value;

@Value
public class Product {
    UUID id;
    String sku;
    String name;
    String description;
    UUID categoryId;
    UnitOfMeasure unitOfMeasure;
    boolean active;
}
