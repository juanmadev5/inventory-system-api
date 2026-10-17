package com.jm.inventory.domain.cyclecount;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface CycleCountRepository {

    /**
     * Upserts the session's own fields and every one of its lines - a line is inserted the
     * first time it appears, updated (only {@code countedQuantity} ever changes) on every
     * later call. Same reasoning as {@code PurchaseOrderRepository.save} for why lines live
     * in their own table rather than a JPA {@code @OneToMany}.
     */
    CycleCount save(CycleCount cycleCount);

    Optional<CycleCount> findById(UUID id);

    PagedResult<CycleCount> findAll(CycleCountFilter filter, int page, int pageSize);
}
