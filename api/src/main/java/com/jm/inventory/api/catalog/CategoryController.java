package com.jm.inventory.api.catalog;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.catalog.CategoryService;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.CategoryFilter;
import com.jm.inventory.domain.common.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
@RequireStaff
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List categories.")
    @ApiResponse(responseCode = "200", description = "Paginated list of categories")
    public PageResponse<CategoryResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) UUID parentCategoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<Category> result =
                categoryService.list(new CategoryFilter(name, parentCategoryId), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(CategoryResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a category.")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "409", description = "CATEGORY_NAME_ALREADY_EXISTS")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        Category created = categoryService.create(request.name(), request.parentCategoryId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(CategoryResponse.from(created));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get a category by id.")
    @ApiResponse(responseCode = "200", description = "Category found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public CategoryResponse getById(@Parameter(description = "Category id") @PathVariable UUID categoryId) {
        return CategoryResponse.from(categoryService.get(categoryId));
    }

    @PatchMapping("/{categoryId}")
    @RequireManager
    @Operation(summary = "Partially update a category.")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "CATEGORY_NAME_ALREADY_EXISTS")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public CategoryResponse update(
            @PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        Category updated = categoryService.update(categoryId, request.name(), request.parentCategoryId());
        return CategoryResponse.from(updated);
    }
}
