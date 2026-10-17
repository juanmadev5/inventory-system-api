package com.jm.inventory.infrastructure.purchasing;

import com.jm.inventory.domain.purchasing.PurchaseOrderLineCostRepository;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderLineSpringDataRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderLineCostRepositoryAdapter implements PurchaseOrderLineCostRepository {

    private final PurchaseOrderLineSpringDataRepository springDataRepository;

    public PurchaseOrderLineCostRepositoryAdapter(PurchaseOrderLineSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<BigDecimal> averageUnitCostForProduct(UUID productId) {
        return Optional.ofNullable(springDataRepository.averageUnitCostForProduct(productId));
    }
}
