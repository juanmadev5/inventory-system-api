package com.jm.inventory.api.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.user.Role;
import com.jm.inventory.infrastructure.catalog.persistence.CategoryJpaEntity;
import com.jm.inventory.infrastructure.catalog.persistence.ProductJpaEntity;
import com.jm.inventory.infrastructure.platform.cache.CacheNames;
import com.jm.inventory.infrastructure.warehouse.persistence.WarehouseJpaEntity;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The real Redis cache stack, end to end (docs/specs/15-reference-data-caching/), against
 * the shared local Redis - the same deliberate exception the rate-limit tests make (see
 * docs/conventions/testing.md). Every other integration test runs with
 * {@code spring.cache.type=simple}. Postgres reads are counted with Hibernate's own
 * statistics, so "served from cache" means "no entity load happened," not a guess.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class ReferenceDataCachingIntegrationTest {

    private static final List<String> ALL_CACHE_NAMES =
            List.of(CacheNames.PRODUCTS_BY_ID, CacheNames.WAREHOUSES_BY_ID, CacheNames.CATEGORIES_BY_ID);
    private static final Duration CONFIGURED_TTL = Duration.ofMinutes(10);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "redis");
        registry.add("app.cache.ttl.products", CONFIGURED_TTL::toString);
        registry.add("app.cache.ttl.warehouses", CONFIGURED_TTL::toString);
        registry.add("app.cache.ttl.categories", CONFIGURED_TTL::toString);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        // See WarehouseControllerIntegrationTest - shared local Redis, shared mocked subject.
        registry.add("app.rate-limit.api-wide.max-requests", () -> 1_000_000);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private JwtRequestPostProcessor authenticatedAsManager;

    @BeforeEach
    void setUp() {
        ALL_CACHE_NAMES.forEach(cacheName -> cacheManager.getCache(cacheName).clear());
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        authenticatedAsManager = jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_aCachedProduct_when_readTwice_then_postgresIsReadOnceAndBothBodiesMatch() throws Exception {
        // Given
        String productId = createProduct(createCategory());
        statistics.clear();

        // When
        String firstBody = getBody("/api/v1/products/" + productId);
        String secondBody = getBody("/api/v1/products/" + productId);

        // Then
        assertThat(loadCount(ProductJpaEntity.class)).isEqualTo(1);
        assertThat(secondBody).isEqualTo(firstBody);
    }

    @Test
    void given_aCachedWarehouseAndCategory_when_readTwice_then_postgresIsReadOncePerEntity() throws Exception {
        // Given
        String categoryId = createCategory();
        String warehouseId = createWarehouse();
        statistics.clear();

        // When
        getBody("/api/v1/categories/" + categoryId);
        getBody("/api/v1/categories/" + categoryId);
        getBody("/api/v1/warehouses/" + warehouseId);
        getBody("/api/v1/warehouses/" + warehouseId);

        // Then
        assertThat(loadCount(CategoryJpaEntity.class)).isEqualTo(1);
        assertThat(loadCount(WarehouseJpaEntity.class)).isEqualTo(1);
    }

    @Test
    void given_aCachedProduct_when_recordingAStockMovement_then_theProductIsNotReadFromPostgres() throws Exception {
        // Given
        String productId = createProduct(createCategory());
        String warehouseId = createWarehouse();
        getBody("/api/v1/products/" + productId);
        statistics.clear();

        // When
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(movementJson(productId, warehouseId)))
                .andExpect(status().isCreated());

        // Then
        assertThat(loadCount(ProductJpaEntity.class)).isZero();
    }

    @Test
    void given_aCachedProduct_when_patched_then_theNextReadReturnsTheNewValues() throws Exception {
        // Given
        String productId = createProduct(createCategory());
        getBody("/api/v1/products/" + productId);

        // When
        mockMvc.perform(patch("/api/v1/products/" + productId).with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed Widget\"}"))
                .andExpect(status().isOk());

        // Then
        mockMvc.perform(get("/api/v1/products/" + productId).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Widget")));
    }

    @Test
    void given_aCachedWarehouse_when_deactivated_then_aMovementAgainstItIsRejectedImmediately() throws Exception {
        // Given
        String productId = createProduct(createCategory());
        String warehouseId = createWarehouse();
        getBody("/api/v1/warehouses/" + warehouseId);

        // When
        mockMvc.perform(patch("/api/v1/warehouses/" + warehouseId).with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        // Then
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(movementJson(productId, warehouseId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("WAREHOUSE_INACTIVE")));
    }

    @Test
    void given_aCachedProduct_when_deactivated_then_aReceiptForItIsRejectedImmediately() throws Exception {
        // Given
        String productId = createProduct(createCategory());
        String warehouseId = createWarehouse();
        getBody("/api/v1/products/" + productId);

        // When
        mockMvc.perform(patch("/api/v1/products/" + productId).with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        // Then
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(movementJson(productId, warehouseId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_INACTIVE")));
    }

    @Test
    void given_aCachedProduct_when_aWriteRollsBack_then_theEntryIsKeptAndStillHoldsTheOriginalValue() throws Exception {
        // Given
        UUID productId = UUID.fromString(createProduct(createCategory()));
        Product original = productRepository.findById(productId).orElseThrow();
        assertThat(redisTemplate.hasKey(productKey(productId))).isTrue();

        // When
        transactionTemplate.executeWithoutResult(transaction -> {
            productRepository.save(new Product(original.getId(), original.getSku(), "Rolled Back Name",
                    original.getDescription(), original.getCategoryId(), original.getUnitOfMeasure(), original.isActive()));
            transaction.setRollbackOnly();
        });

        // Then
        assertThat(redisTemplate.hasKey(productKey(productId))).isTrue();
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo(original.getName());
    }

    @Test
    void given_anUncachedProduct_when_readInsideATransactionThatRollsBack_then_nothingIsCached() throws Exception {
        // Given
        UUID productId = UUID.fromString(createProduct(createCategory()));

        // When
        transactionTemplate.executeWithoutResult(transaction -> {
            productRepository.findById(productId);
            transaction.setRollbackOnly();
        });

        // Then
        assertThat(redisTemplate.hasKey(productKey(productId))).isFalse();
    }

    @Test
    void given_anUnknownProductId_when_read_then_notFoundAndNoKeyIsWritten() throws Exception {
        // Given
        UUID unknownProductId = UUID.randomUUID();

        // When / Then
        mockMvc.perform(get("/api/v1/products/" + unknownProductId).with(authenticatedAsManager))
                .andExpect(status().isNotFound());
        assertThat(redisTemplate.hasKey(productKey(unknownProductId))).isFalse();
    }

    @Test
    void given_aCachedProduct_when_inspectingRedis_then_theKeyIsPrefixedAndExpiresWithinTheConfiguredTtl()
            throws Exception {
        // Given
        UUID productId = UUID.fromString(createProduct(createCategory()));

        // When
        getBody("/api/v1/products/" + productId);

        // Then
        Long secondsToLive = redisTemplate.getExpire(productKey(productId));
        assertThat(secondsToLive).isPositive().isLessThanOrEqualTo(CONFIGURED_TTL.toSeconds());
    }

    @Test
    void given_aCacheNameThatIsNotDeclared_when_requested_then_noCacheIsCreated() {
        assertThat(cacheManager.getCache("not-a-declared-cache")).isNull();
    }

    private long loadCount(Class<?> entityType) {
        return statistics.getEntityStatistics(entityType.getName()).getLoadCount();
    }

    private String productKey(UUID productId) {
        return CacheNames.KEY_PREFIX + CacheNames.PRODUCTS_BY_ID + "::" + productId;
    }

    private String getBody(String path) throws Exception {
        return mockMvc.perform(get(path).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String movementJson(String productId, String warehouseId) {
        return "{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                + "\",\"type\":\"RECEIPT\",\"quantity\":5}";
    }

    private String createCategory() throws Exception {
        return idFromLocation(mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Category " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String createProduct(String categoryId) throws Exception {
        return idFromLocation(mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-" + UUID.randomUUID() + "\",\"name\":\"Widget\",\"categoryId\":\""
                                + categoryId + "\",\"unitOfMeasure\":\"EACH\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String createWarehouse() throws Exception {
        return idFromLocation(mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
