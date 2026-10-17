package com.jm.inventory.infrastructure.cyclecount.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Only {@code countedQuantity} ever changes after creation - starts {@code null}. */
@Entity
@Table(name = "cycle_count_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "cycle_count_id", nullable = false)
    private UUID cycleCountId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;
}
