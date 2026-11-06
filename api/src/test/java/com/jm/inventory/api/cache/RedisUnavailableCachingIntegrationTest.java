package com.jm.inventory.api.cache;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import com.jm.inventory.domain.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Redis cache stack with Redis unreachable (a port nothing listens on): every cached
 * read, put and after-commit eviction must fail open - the request still succeeds against
 * Postgres. See docs/specs/15-reference-data-caching/requirements.md, criterion 8.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class RedisUnavailableCachingIntegrationTest {

    private static final int UNREACHABLE_REDIS_PORT = 1;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.port", () -> UNREACHABLE_REDIS_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    private JwtRequestPostProcessor authenticatedAsManager;

    @BeforeEach
    void setUpAuthenticatedJwt() {
        authenticatedAsManager = jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_redisIsUnreachable_when_readingUpdatingAndMovingStock_then_everyRequestStillSucceeds()
            throws Exception {
        // Given
        String categoryId = idFromLocation(mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        String productId = idFromLocation(mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"categoryId\":\"" + categoryId
                                + "\",\"unitOfMeasure\":\"EACH\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        String warehouseId = idFromLocation(mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        // When / Then
        mockMvc.perform(get("/api/v1/products/" + productId).with(authenticatedAsManager))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/products/" + productId).with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed Widget\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                                + "\",\"type\":\"RECEIPT\",\"quantity\":5}"))
                .andExpect(status().isCreated());
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
