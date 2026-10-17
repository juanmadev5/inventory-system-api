package com.jm.inventory.infrastructure.alerting;

import com.jm.inventory.domain.alerting.StockAlert;
import com.jm.inventory.domain.alerting.StockAlertFilter;
import com.jm.inventory.domain.alerting.StockAlertRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.infrastructure.alerting.persistence.StockAlertProjection;
import com.jm.inventory.infrastructure.alerting.persistence.StockAlertSpringDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class StockAlertRepositoryAdapter implements StockAlertRepository {

    private final StockAlertSpringDataRepository springDataRepository;

    public StockAlertRepositoryAdapter(StockAlertSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public PagedResult<StockAlert> findAll(StockAlertFilter filter, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<StockAlertProjection> result = springDataRepository.findAlerts(filter.warehouseId(), pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private StockAlert toDomain(StockAlertProjection projection) {
        int quantityOnHand = projection.getQuantityOnHand() != null ? projection.getQuantityOnHand() : 0;
        return new StockAlert(
                projection.getId(), projection.getProductId(), projection.getWarehouseId(), quantityOnHand,
                projection.getReorderPoint(), projection.getReorderQuantity());
    }
}
