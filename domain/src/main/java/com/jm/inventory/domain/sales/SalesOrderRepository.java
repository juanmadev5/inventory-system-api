package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository {

    /**
     * Upserts the order's own fields and every one of its lines - a line is inserted the
     * first time it appears, updated (only {@code quantityShipped} ever changes) on every
     * later call. Same reasoning as {@code PurchaseOrderRepository.save} for why lines live
     * in their own table rather than a JPA {@code @OneToMany}.
     */
    SalesOrder save(SalesOrder salesOrder);

    Optional<SalesOrder> findById(UUID id);

    PagedResult<SalesOrder> findAll(SalesOrderFilter filter, int page, int pageSize);
}
