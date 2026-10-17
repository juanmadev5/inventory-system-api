package com.jm.inventory.infrastructure.catalog.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategorySpringDataRepository
        extends JpaRepository<CategoryJpaEntity, UUID>, JpaSpecificationExecutor<CategoryJpaEntity> {

    Optional<CategoryJpaEntity> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
