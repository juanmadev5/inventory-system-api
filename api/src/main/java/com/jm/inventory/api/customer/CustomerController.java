package com.jm.inventory.api.customer;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.customer.CustomerService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.customer.Customer;
import com.jm.inventory.domain.customer.CustomerFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
@RequireStaff
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "List customers.")
    @ApiResponse(responseCode = "200", description = "Paginated list of customers")
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<Customer> result = customerService.list(new CustomerFilter(name), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(CustomerResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a customer.")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @ApiResponse(responseCode = "409", description = "CUSTOMER_NAME_ALREADY_EXISTS")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer created = customerService.create(request.name(), request.email(), request.phone());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(created));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer by id.")
    @ApiResponse(responseCode = "200", description = "Customer found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public CustomerResponse getById(@PathVariable UUID customerId) {
        return CustomerResponse.from(customerService.get(customerId));
    }

    @PatchMapping("/{customerId}")
    @RequireManager
    @Operation(summary = "Partially update a customer.")
    @ApiResponse(responseCode = "200", description = "Customer updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "CUSTOMER_NAME_ALREADY_EXISTS")
    public CustomerResponse update(@PathVariable UUID customerId, @Valid @RequestBody UpdateCustomerRequest request) {
        Customer updated = customerService.update(customerId, request.name(), request.email(), request.phone());
        return CustomerResponse.from(updated);
    }
}
