package com.jm.inventory.application.warehouse;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.warehouse.Warehouse;
import com.jm.inventory.domain.warehouse.WarehouseFilter;
import com.jm.inventory.domain.warehouse.WarehouseNameAlreadyExistsException;
import com.jm.inventory.domain.warehouse.WarehouseNotFoundException;
import com.jm.inventory.domain.warehouse.WarehouseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final AuditLogService auditLogService;

    public WarehouseService(WarehouseRepository warehouseRepository, AuditLogService auditLogService) {
        this.warehouseRepository = warehouseRepository;
        this.auditLogService = auditLogService;
    }

    public Warehouse create(String name, String address) {
        if (warehouseRepository.existsByName(name)) {
            throw new WarehouseNameAlreadyExistsException(name);
        }
        Warehouse created = warehouseRepository.save(new Warehouse(UUID.randomUUID(), name, address, true));
        auditLogService.recordCreated(AuditEntityType.WAREHOUSE, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Warehouse get(UUID id) {
        return warehouseRepository.findById(id).orElseThrow(() -> new WarehouseNotFoundException(id));
    }

    public Warehouse update(UUID id, String name, String address, Boolean active) {
        Warehouse existing = get(id);
        if (name != null && warehouseRepository.existsByNameAndIdNot(name, id)) {
            throw new WarehouseNameAlreadyExistsException(name);
        }
        Warehouse updated = new Warehouse(
                existing.getId(),
                name != null ? name : existing.getName(),
                address != null ? address : existing.getAddress(),
                active != null ? active : existing.isActive());
        Warehouse saved = warehouseRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.WAREHOUSE, id, existing, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PagedResult<Warehouse> list(WarehouseFilter filter, Integer page, Integer pageSize) {
        return warehouseRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }
}
