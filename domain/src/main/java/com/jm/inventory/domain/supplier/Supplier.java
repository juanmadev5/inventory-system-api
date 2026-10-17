package com.jm.inventory.domain.supplier;

import java.util.UUID;
import lombok.Value;

@Value
public class Supplier {
    UUID id;
    String name;
    String email;
    String phone;
}
