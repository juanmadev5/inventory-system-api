package com.jm.inventory.infrastructure.stock.persistence;

import com.jm.inventory.domain.stock.StockMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only - no setters, no update path. Every row is written once by
 * {@code StockMovementRepositoryAdapter.save} and never touched again.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String reference;

    @Column(name = "related_movement_id")
    private UUID relatedMovementId;
}
