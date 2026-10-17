package com.jm.inventory.infrastructure.cyclecount.persistence;

import com.jm.inventory.domain.cyclecount.CycleCountScope;
import com.jm.inventory.domain.cyclecount.CycleCountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lines live in their own table ({@link CycleCountLineJpaEntity}, plain
 * {@code cycle_count_id} column) - not a JPA {@code @OneToMany} - see
 * {@code CycleCountRepositoryAdapter}, same discipline as {@code PurchaseOrderJpaEntity}.
 */
@Entity
@Table(name = "cycle_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleCountScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleCountStatus status;
}
