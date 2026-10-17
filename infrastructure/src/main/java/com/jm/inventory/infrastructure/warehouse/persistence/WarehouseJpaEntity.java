package com.jm.inventory.infrastructure.warehouse.persistence;

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

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(nullable = false)
    private boolean active;
}
