package com.jm.inventory.api.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @Size(max = 200) String name,
        @Email String email,
        String phone) {
}
