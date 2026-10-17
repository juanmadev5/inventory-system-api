package com.jm.inventory.api.customer;

import com.jm.inventory.domain.customer.Customer;
import java.util.UUID;

public record CustomerResponse(UUID id, String name, String email, String phone) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone());
    }
}
