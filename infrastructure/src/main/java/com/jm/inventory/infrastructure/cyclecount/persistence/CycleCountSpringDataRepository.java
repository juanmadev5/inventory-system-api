package com.jm.inventory.infrastructure.cyclecount.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CycleCountSpringDataRepository
        extends JpaRepository<CycleCountJpaEntity, UUID>, JpaSpecificationExecutor<CycleCountJpaEntity> {
}
