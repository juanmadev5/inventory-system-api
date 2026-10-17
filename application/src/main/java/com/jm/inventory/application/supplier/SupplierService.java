package com.jm.inventory.application.supplier;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.supplier.Supplier;
import com.jm.inventory.domain.supplier.SupplierFilter;
import com.jm.inventory.domain.supplier.SupplierNameAlreadyExistsException;
import com.jm.inventory.domain.supplier.SupplierNotFoundException;
import com.jm.inventory.domain.supplier.SupplierRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    public SupplierService(SupplierRepository supplierRepository, AuditLogService auditLogService) {
        this.supplierRepository = supplierRepository;
        this.auditLogService = auditLogService;
    }

    public Supplier create(String name, String email, String phone) {
        if (supplierRepository.existsByName(name)) {
            throw new SupplierNameAlreadyExistsException(name);
        }
        Supplier created = supplierRepository.save(new Supplier(UUID.randomUUID(), name, email, phone));
        auditLogService.recordCreated(AuditEntityType.SUPPLIER, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return supplierRepository.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    public Supplier update(UUID id, String name, String email, String phone) {
        Supplier existing = get(id);
        if (name != null && supplierRepository.existsByNameAndIdNot(name, id)) {
            throw new SupplierNameAlreadyExistsException(name);
        }
        Supplier updated = new Supplier(
                id,
                name != null ? name : existing.getName(),
                email != null ? email : existing.getEmail(),
                phone != null ? phone : existing.getPhone());
        Supplier saved = supplierRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.SUPPLIER, id, existing, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PagedResult<Supplier> list(SupplierFilter filter, Integer page, Integer pageSize) {
        return supplierRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }
}
