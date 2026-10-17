package com.jm.inventory.domain.warehouse;

import java.util.UUID;
import lombok.Value;

@Value
public class Warehouse {
    UUID id;
    String name;
    String address;
    boolean active;
}
