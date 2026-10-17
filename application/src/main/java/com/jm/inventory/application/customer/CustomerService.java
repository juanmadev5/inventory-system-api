package com.jm.inventory.application.customer;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.customer.Customer;
import com.jm.inventory.domain.customer.CustomerFilter;
import com.jm.inventory.domain.customer.CustomerNameAlreadyExistsException;
import com.jm.inventory.domain.customer.CustomerNotFoundException;
import com.jm.inventory.domain.customer.CustomerRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public CustomerService(CustomerRepository customerRepository, AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    public Customer create(String name, String email, String phone) {
        if (customerRepository.existsByName(name)) {
            throw new CustomerNameAlreadyExistsException(name);
        }
        Customer created = customerRepository.save(new Customer(UUID.randomUUID(), name, email, phone));
        auditLogService.recordCreated(AuditEntityType.CUSTOMER, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public Customer update(UUID id, String name, String email, String phone) {
        Customer existing = get(id);
        if (name != null && customerRepository.existsByNameAndIdNot(name, id)) {
            throw new CustomerNameAlreadyExistsException(name);
        }
        Customer updated = new Customer(
                id,
                name != null ? name : existing.getName(),
                email != null ? email : existing.getEmail(),
                phone != null ? phone : existing.getPhone());
        Customer saved = customerRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.CUSTOMER, id, existing, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PagedResult<Customer> list(CustomerFilter filter, Integer page, Integer pageSize) {
        return customerRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }
}
