package com.jm.inventory.infrastructure.purchasing.persistence;

import com.jm.inventory.domain.purchasing.PurchaseOrderStatus;
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
 * Lines live in their own table ({@link PurchaseOrderLineJpaEntity}, plain
 * {@code purchase_order_id} column) - not a JPA {@code @OneToMany} - see
 * {@code PurchaseOrderRepositoryAdapter}.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;
}
