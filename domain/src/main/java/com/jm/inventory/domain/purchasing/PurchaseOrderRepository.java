package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository {

    /**
     * Upserts the order's own fields and every one of its lines - a line is inserted the
     * first time it appears, updated (only {@code quantityReceived} ever changes) on every
     * later call. See the adapter for why lines live in their own table rather than a JPA
     * {@code @OneToMany}.
     */
    PurchaseOrder save(PurchaseOrder purchaseOrder);

    Optional<PurchaseOrder> findById(UUID id);

    PagedResult<PurchaseOrder> findAll(PurchaseOrderFilter filter, int page, int pageSize);
}
