package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class UnknownCustomerReferenceException extends InvalidReferenceException {

    public UnknownCustomerReferenceException(UUID customerId) {
        super("customerId", "Customer " + customerId + " does not exist");
    }
}
