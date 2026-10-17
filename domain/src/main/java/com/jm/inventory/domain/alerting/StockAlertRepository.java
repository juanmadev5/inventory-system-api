package com.jm.inventory.domain.alerting;

import com.jm.inventory.domain.common.PagedResult;

public interface StockAlertRepository {

    PagedResult<StockAlert> findAll(StockAlertFilter filter, int page, int pageSize);
}
