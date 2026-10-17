package com.jm.inventory.infrastructure.sales.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Only {@code quantityShipped} ever changes after creation - same spirit as
 * {@code PurchaseOrderLineJpaEntity}.
 */
@Entity
@Table(name = "sales_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity_ordered", nullable = false)
    private int quantityOrdered;

    @Column(name = "quantity_shipped", nullable = false)
    private int quantityShipped;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
}
