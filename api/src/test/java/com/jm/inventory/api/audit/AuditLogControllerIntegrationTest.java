package com.jm.inventory.api.audit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jm.inventory.domain.user.Role;
import java.util.UUID;
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
 * why the JWT is mocked and the Testcontainers Postgres is real - same scope here. Exercises
 * {@code AuditLogService}'s wiring into {@code CategoryService} as the representative case;
 * see docs/specs/07-audit-trail/requirements.md for why every audited entity shares the same
 * generic recording path, so one entity is enough to prove the mechanism.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class AuditLogControllerIntegrationTest {

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
    private JwtRequestPostProcessor authenticatedAsAdmin;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsAdmin = jwt()
                .authorities(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingAuditEntries_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aManagerRoleToken_when_listingAuditEntries_then_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit").with(authenticatedAsManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aCategoryCreated_when_listingAuditEntriesAsAdmin_then_aCreatedEntryAppears() throws Exception {
        createCategory("Electronics");

        mockMvc.perform(get("/api/v1/audit?entityType=CATEGORY&action=CREATED").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action", is("CREATED")))
                .andExpect(jsonPath("$.items[0].entityType", is("CATEGORY")))
                .andExpect(jsonPath("$.items[0].oldValue", is(nullValue())))
                .andExpect(jsonPath("$.items[0].newValue.name", is("Electronics")));
    }

    @Test
    void given_aCategoryUpdated_when_listingAuditEntriesAsAdmin_then_anUpdatedEntryShowsOldAndNewValues()
            throws Exception {
        String categoryId = createCategory("Electronics");

        mockMvc.perform(patch("/api/v1/categories/" + categoryId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Consumer Electronics\"}"));

        mockMvc.perform(get("/api/v1/audit?entityType=CATEGORY&action=UPDATED").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].oldValue.name", is("Electronics")))
                .andExpect(jsonPath("$.items[0].newValue.name", is("Consumer Electronics")));
    }

    @Test
    void given_aRejectedDuplicateCategoryName_when_listingAuditEntries_then_noExtraEntryIsRecorded()
            throws Exception {
        createCategory("Electronics");

        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/audit?entityType=CATEGORY&action=CREATED").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)));
    }

    @Test
    void given_anUnknownAuditEntryId_when_gettingItAsAdmin_then_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/audit/" + UUID.randomUUID()).with(authenticatedAsAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_aCategoryCreated_when_gettingItsAuditEntryByIdAsAdmin_then_found() throws Exception {
        createCategory("Electronics");

        MvcResult listResult = mockMvc
                .perform(get("/api/v1/audit?entityType=CATEGORY&action=CREATED").with(authenticatedAsAdmin))
                .andReturn();
        String entryId = JsonPath.read(listResult.getResponse().getContentAsString(), "$.items[0].id");

        mockMvc.perform(get("/api/v1/audit/" + entryId).with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType", is("CATEGORY")));
    }

    private String createCategory(String name) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
