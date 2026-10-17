package com.jm.inventory.domain.supplier;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(UUID id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    PagedResult<Supplier> findAll(SupplierFilter filter, int page, int pageSize);
}
