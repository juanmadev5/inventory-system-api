package com.jm.inventory.api.warehouse;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.domain.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * See {@link com.jm.inventory.api.catalog.CategoryControllerIntegrationTest}'s Javadoc for
 * why the JWT is mocked and the Testcontainers Postgres is real - same scope and reasoning
 * here, applied to {@code /api/v1/warehouses}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class WarehouseControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "simple");
        // Many test classes share one real local Redis across a single test run under
        // the same mocked JWT subject - without this, their cumulative request count
        // can cross the production default and spuriously trip ApiRateLimitFilter.
        registry.add("app.rate-limit.api-wide.max-requests", () -> 1_000_000);
    }

    @Autowired
    private MockMvc mockMvc;

    private JwtRequestPostProcessor authenticatedAsManager;
    private JwtRequestPostProcessor authenticatedAsStaff;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        // MANAGER - warehouse create/update requires at least MANAGER, per
        // docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingWarehouses_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_creatingAWarehouse_then_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"name\":\"Main Warehouse\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_validRequest_when_creatingAWarehouse_then_createdAndRetrievable() throws Exception {
        MvcResult createResult = mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Main Warehouse\",\"address\":\"123 Main St\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Main Warehouse")))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn();

        String warehouseId = idFromLocation(createResult);

        mockMvc.perform(get("/api/v1/warehouses/" + warehouseId).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address", is("123 Main St")));
    }

    @Test
    void given_aWarehouseAlreadyNamedMain_when_creatingAnotherWithTheSameName_then_conflict() throws Exception {
        mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Main Warehouse\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Main Warehouse\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("WAREHOUSE_NAME_ALREADY_EXISTS")));
    }

    @Test
    void given_aBlankName_when_creatingAWarehouse_then_badRequestFromBeanValidation() throws Exception {
        mockMvc.perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_anActiveWarehouse_when_deactivatingIt_then_filterableByActive() throws Exception {
        MvcResult createResult = mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Main Warehouse\"}"))
                .andReturn();
        String warehouseId = idFromLocation(createResult);

        mockMvc.perform(patch("/api/v1/warehouses/" + warehouseId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(get("/api/v1/warehouses?active=true").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/warehouses?active=false").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
