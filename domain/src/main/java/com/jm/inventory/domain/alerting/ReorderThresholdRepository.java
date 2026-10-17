package com.jm.inventory.domain.alerting;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface ReorderThresholdRepository {

    ReorderThreshold save(ReorderThreshold reorderThreshold);

    Optional<ReorderThreshold> findById(UUID id);

    Optional<ReorderThreshold> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    PagedResult<ReorderThreshold> findAll(ReorderThresholdFilter filter, int page, int pageSize);
}
