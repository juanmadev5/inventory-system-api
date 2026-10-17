package com.jm.inventory.infrastructure.purchasing.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderLineSpringDataRepository extends JpaRepository<PurchaseOrderLineJpaEntity, UUID> {

    List<PurchaseOrderLineJpaEntity> findByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * {@code null} (via a single-row {@code avg()} with no matches) if the product has
     * never appeared on any purchase order line - see
     * docs/specs/10-reporting/requirements.md.
     */
    @Query("select avg(l.unitCost) from PurchaseOrderLineJpaEntity l where l.productId = :productId")
    BigDecimal averageUnitCostForProduct(@Param("productId") UUID productId);
}
