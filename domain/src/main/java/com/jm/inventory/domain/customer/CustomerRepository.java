package com.jm.inventory.domain.customer;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    PagedResult<Customer> findAll(CustomerFilter filter, int page, int pageSize);
}
