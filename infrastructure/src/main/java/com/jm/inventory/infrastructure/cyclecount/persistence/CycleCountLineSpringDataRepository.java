package com.jm.inventory.infrastructure.cyclecount.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CycleCountLineSpringDataRepository extends JpaRepository<CycleCountLineJpaEntity, UUID> {

    List<CycleCountLineJpaEntity> findByCycleCountId(UUID cycleCountId);
}
