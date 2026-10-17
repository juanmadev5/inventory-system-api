package com.jm.inventory.api.user;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jm.inventory.application.user.PasswordHasher;
import com.jm.inventory.domain.user.Role;
import com.jm.inventory.domain.user.User;
import com.jm.inventory.domain.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the real login path end to end - a real user row, a real signed/decoded JWT
 * (never mocked, unlike every other controller test in this suite) - then proves the issued
 * token actually authenticates against a protected endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void given_aValidEmailAndPassword_when_loggingIn_then_issuesABearerTokenThatAuthenticates() throws Exception {
        // Given
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        userRepository.save(new User(
                UUID.randomUUID(), email, passwordHasher.hash("correct-password"), Role.STAFF, true, Instant.now()));

        // When
        MvcResult loginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        // Then
        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void given_aWrongPassword_when_loggingIn_then_unauthorized() throws Exception {
        // Given
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        userRepository.save(new User(
                UUID.randomUUID(), email, passwordHasher.hash("correct-password"), Role.STAFF, true, Instant.now()));

        // When / Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void given_anUnknownEmail_when_loggingIn_then_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void given_aDeactivatedUser_when_loggingIn_then_unauthorized() throws Exception {
        // Given
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        userRepository.save(new User(
                UUID.randomUUID(), email, passwordHasher.hash("correct-password"), Role.STAFF, false, Instant.now()));

        // When / Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }
}
