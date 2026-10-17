package com.jm.inventory.application.stock;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelFilter;
import com.jm.inventory.domain.stock.StockLevelNotFoundException;
import com.jm.inventory.domain.stock.StockLevelRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only in this feature - see docs/specs/01-catalog-and-stock/requirements.md. Writes
 * arrive with the Stock Movements feature.
 */
@Service
@Transactional(readOnly = true)
public class StockLevelService {

    private final StockLevelRepository stockLevelRepository;

    public StockLevelService(StockLevelRepository stockLevelRepository) {
        this.stockLevelRepository = stockLevelRepository;
    }

    public StockLevel get(UUID id) {
        return stockLevelRepository.findById(id).orElseThrow(() -> new StockLevelNotFoundException(id));
    }

    public PagedResult<StockLevel> list(StockLevelFilter filter, Integer page, Integer pageSize) {
        return stockLevelRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }
}
