package com.jm.inventory.api.catalog;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Exercises the real HTTP path (JSON (de)serialization, Bean Validation, exception ->
 * ProblemDetail mapping) end to end. The bearer JWT is mocked
 * ({@code SecurityMockMvcRequestPostProcessors.jwt()}), never a real signed token - this
 * test is about everything on top of authentication, not authentication itself. Request/
 * response bodies are handled as raw JSON strings - deliberately no injected
 * {@code ObjectMapper} dependency for this test to carry.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class CategoryControllerIntegrationTest {

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
    private JwtRequestPostProcessor authenticatedWithNoRole;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        // MANAGER - every write in this suite (category create/update) requires at least
        // MANAGER, per docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
        authenticatedWithNoRole = jwt().authorities();
    }

    @Test
    void given_noAuthentication_when_listingCategories_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aTokenWithNoRecognizedRole_when_listingCategories_then_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/categories").with(authenticatedWithNoRole))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aStaffRoleToken_when_creatingACategory_then_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_validRequest_when_creatingACategory_then_createdAndRetrievable() throws Exception {
        MvcResult createResult = mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Electronics")))
                .andReturn();

        String categoryId = createdId(createResult);

        mockMvc.perform(get("/api/v1/categories/" + categoryId).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Electronics")));
    }

    @Test
    void given_aCategoryAlreadyNamedElectronics_when_creatingAnotherWithTheSameName_then_conflict() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Electronics\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CATEGORY_NAME_ALREADY_EXISTS")));
    }

    @Test
    void given_anUnknownParentCategoryId_when_creatingACategory_then_unprocessable() throws Exception {
        String requestBody = "{\"name\":\"Cables\",\"parentCategoryId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("parentCategoryId")));
    }

    @Test
    void given_aBlankName_when_creatingACategory_then_badRequestFromBeanValidation() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_anExistingCategory_when_patchingOnlyTheName_then_parentCategoryIdIsUnchanged() throws Exception {
        MvcResult createResult = mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Electronics\"}"))
                .andReturn();
        String categoryId = createdId(createResult);

        mockMvc.perform(patch("/api/v1/categories/" + categoryId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"name\":\"Consumer Electronics\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Consumer Electronics")))
                .andExpect(jsonPath("$.parentCategoryId").doesNotExist());
    }

    private String createdId(MvcResult createResult) {
        String location = createResult.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
