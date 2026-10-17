package com.jm.inventory.domain.customer;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class CustomerNotFoundException extends NotFoundException {

    public CustomerNotFoundException(UUID id) {
        super("Customer " + id + " not found");
    }

    @Override
    public String code() {
        return "CUSTOMER_NOT_FOUND";
    }
}
