package com.jm.inventory.api.catalog;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.catalog.ProductCsvExportService;
import com.jm.inventory.application.catalog.ProductCsvImportService;
import com.jm.inventory.application.catalog.ProductImportResult;
import com.jm.inventory.application.catalog.ProductService;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductFilter;
import com.jm.inventory.domain.common.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
@RequireStaff
public class ProductController {

    private static final String ACTIVE_ANY = "any";
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");

    private final ProductService productService;
    private final ProductCsvImportService productCsvImportService;
    private final ProductCsvExportService productCsvExportService;

    public ProductController(
            ProductService productService, ProductCsvImportService productCsvImportService,
            ProductCsvExportService productCsvExportService) {
        this.productService = productService;
        this.productCsvImportService = productCsvImportService;
        this.productCsvExportService = productCsvExportService;
    }

    @GetMapping
    @Operation(summary = "List products. Defaults to active-only; pass active=any to include inactive products.")
    @ApiResponse(responseCode = "200", description = "Paginated list of products")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        ProductFilter filter = new ProductFilter(categoryId, resolveActiveFilter(active), sku);
        PagedResult<Product> result = productService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(ProductResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a product.")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "409", description = "PRODUCT_SKU_ALREADY_EXISTS")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        Product created = productService.create(
                request.sku(), request.name(), request.description(), request.categoryId(), request.unitOfMeasure());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProductResponse.from(created));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a product by id.")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public ProductResponse getById(@PathVariable UUID productId) {
        return ProductResponse.from(productService.get(productId));
    }

    @PatchMapping("/{productId}")
    @RequireManager
    @Operation(summary = "Partially update a product, including deactivating it (active=false).")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ProductResponse update(@PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        Product updated = productService.update(
                productId, request.name(), request.description(), request.categoryId(), request.active());
        return ProductResponse.from(updated);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireManager
    @Operation(summary = "Bulk-create products from a CSV file. Row-partial-success, never all-or-nothing.")
    @ApiResponse(responseCode = "200", description = "Import outcome - some or all rows may have failed individually")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED (e.g. more than 1000 data rows)")
    public ProductImportResultResponse importCsv(@RequestParam("file") MultipartFile file) {
        try (Reader csvReader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            ProductImportResult result = productCsvImportService.importFrom(csvReader);
            return ProductImportResultResponse.from(result);
        } catch (IOException unreadableUpload) {
            throw new UncheckedIOException(unreadableUpload);
        }
    }

    @GetMapping("/export")
    @Operation(summary = "Export products as CSV. Same filters as GET /api/v1/products.")
    @ApiResponse(responseCode = "200", description = "CSV body")
    public ResponseEntity<String> export(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String sku) {
        ProductFilter filter = new ProductFilter(categoryId, resolveActiveFilter(active), sku);
        StringWriter csvWriter = new StringWriter();
        productCsvExportService.exportTo(csvWriter, filter);
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.csv\"")
                .body(csvWriter.toString());
    }

    /**
     * {@code null}/absent -> active-only (the default); {@code "any"} -> no filter;
     * otherwise parsed as a boolean. See docs/specs/01-catalog-and-stock/requirements.md.
     */
    private Boolean resolveActiveFilter(String active) {
        if (active == null) {
            return Boolean.TRUE;
        }
        if (ACTIVE_ANY.equalsIgnoreCase(active)) {
            return null;
        }
        return Boolean.parseBoolean(active);
    }
}
