package com.jm.inventory.infrastructure.sales.persistence;

import com.jm.inventory.domain.sales.SalesOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lines live in their own table ({@link SalesOrderLineJpaEntity}, plain
 * {@code sales_order_id} column) - not a JPA {@code @OneToMany} - see
 * {@code SalesOrderRepositoryAdapter}, same discipline as {@code PurchaseOrderJpaEntity}.
 */
@Entity
@Table(name = "sales_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status;

    @Column(name = "expected_ship_date")
    private LocalDate expectedShipDate;
}
