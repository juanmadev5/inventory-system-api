package com.jm.inventory.api.user;

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
import org.testcontainers.junit.jupiter.Testcontainers;

/** See {@code CategoryControllerIntegrationTest}'s Javadoc for why the JWT is mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIntegrationTest {

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

    private JwtRequestPostProcessor authenticatedAsAdmin;
    private JwtRequestPostProcessor authenticatedAsManager;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        authenticatedAsAdmin = jwt().authorities(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
        authenticatedAsManager = jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_aManagerRoleToken_when_listingUsers_then_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(authenticatedAsManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aValidRequest_when_creatingAUser_then_createdWithTemporaryPasswordOnce() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"email\":\"newhire@example.com\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email", is("newhire@example.com")))
                .andExpect(jsonPath("$.user.role", is("STAFF")))
                .andExpect(jsonPath("$.user.active", is(true)))
                .andExpect(jsonPath("$.temporaryPassword").exists());
    }

    @Test
    void given_anEmailAlreadyUsed_when_creatingAnotherUserWithIt_then_conflict() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"email\":\"duplicate@example.com\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"email\":\"duplicate@example.com\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_EMAIL_ALREADY_EXISTS")));
    }

    @Test
    void given_anExistingUser_when_changingTheirRole_then_roleIsReplaced() throws Exception {
        String userId = createUser("promote-me@example.com");

        mockMvc.perform(patch("/api/v1/users/" + userId + "/role").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("MANAGER")));
    }

    @Test
    void given_anExistingUser_when_deactivatingThenReactivating_then_activeToggles() throws Exception {
        String userId = createUser("toggle-me@example.com");

        mockMvc.perform(post("/api/v1/users/" + userId + "/deactivation").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/users/" + userId + "/reactivation").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void given_anUnknownUserId_when_changingRole_then_notFound() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + UUID.randomUUID() + "/role").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("USER_NOT_FOUND")));
    }

    private String createUser(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/users").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"role\":\"STAFF\"}"))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
