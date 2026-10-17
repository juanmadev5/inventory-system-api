package com.jm.inventory.application.alerting;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.alerting.StockAlert;
import com.jm.inventory.domain.alerting.StockAlertFilter;
import com.jm.inventory.domain.alerting.StockAlertRepository;
import com.jm.inventory.domain.common.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockAlertService {

    private final StockAlertRepository stockAlertRepository;

    public StockAlertService(StockAlertRepository stockAlertRepository) {
        this.stockAlertRepository = stockAlertRepository;
    }

    public PagedResult<StockAlert> list(StockAlertFilter filter, Integer page, Integer pageSize) {
        return stockAlertRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }
}
