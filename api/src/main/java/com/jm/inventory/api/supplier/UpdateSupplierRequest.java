package com.jm.inventory.api.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @Size(max = 200) String name,
        @Email String email,
        String phone) {
}
