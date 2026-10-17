package com.jm.inventory.domain.customer;

import java.util.UUID;
import lombok.Value;

@Value
public class Customer {
    UUID id;
    String name;
    String email;
    String phone;
}
