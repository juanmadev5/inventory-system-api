package com.jm.inventory.infrastructure.customer;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.customer.Customer;
import com.jm.inventory.domain.customer.CustomerFilter;
import com.jm.inventory.domain.customer.CustomerRepository;
import com.jm.inventory.infrastructure.customer.persistence.CustomerJpaEntity;
import com.jm.inventory.infrastructure.customer.persistence.CustomerSpecifications;
import com.jm.inventory.infrastructure.customer.persistence.CustomerSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerSpringDataRepository springDataRepository;

    public CustomerRepositoryAdapter(CustomerSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerJpaEntity entity = springDataRepository.findById(customer.getId())
                .map(existing -> applyTo(existing, customer))
                .orElseGet(() -> toNewEntity(customer));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return springDataRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, UUID id) {
        return springDataRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public PagedResult<Customer> findAll(CustomerFilter filter, int page, int pageSize) {
        Specification<CustomerJpaEntity> specification = CustomerSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("name"));
        Page<CustomerJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private CustomerJpaEntity applyTo(CustomerJpaEntity entity, Customer customer) {
        entity.setName(customer.getName());
        entity.setEmail(customer.getEmail());
        entity.setPhone(customer.getPhone());
        return entity;
    }

    private CustomerJpaEntity toNewEntity(Customer customer) {
        return CustomerJpaEntity.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .build();
    }

    private Customer toDomain(CustomerJpaEntity entity) {
        return new Customer(entity.getId(), entity.getName(), entity.getEmail(), entity.getPhone());
    }
}
