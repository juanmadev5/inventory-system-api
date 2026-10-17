package com.jm.inventory.api.webhooks;

import static org.hamcrest.Matchers.hasSize;
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
 * See {@code CategoryControllerIntegrationTest}'s Javadoc for why the JWT is mocked and the
 * Testcontainers Postgres is real - same scope and reasoning here, applied to
 * {@code /api/v1/webhooks/subscriptions}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class WebhookSubscriptionControllerIntegrationTest {

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
        authenticatedAsAdmin = jwt()
                .authorities(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_aManagerRoleToken_when_creatingASubscription_then_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/subscriptions").with(authenticatedAsManager)
                .contentType("application/json")
                .content(subscriptionJson("https://example.com/webhook", "CYCLE_COUNT_APPROVED")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aValidRequest_when_creatingASubscription_then_createdWithSecretOnce() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/subscriptions").with(authenticatedAsAdmin)
                .contentType("application/json")
                .content(subscriptionJson("https://example.com/webhook", "CYCLE_COUNT_APPROVED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", is("https://example.com/webhook")))
                .andExpect(jsonPath("$.eventTypes", hasSize(1)))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.secret").exists());
    }

    @Test
    void given_anEmptyEventTypesList_when_creatingASubscription_then_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/subscriptions").with(authenticatedAsAdmin)
                .contentType("application/json")
                .content("{\"url\":\"https://example.com/webhook\",\"eventTypes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_aCreatedSubscription_when_listing_then_secretIsNeverIncluded() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/subscriptions").with(authenticatedAsAdmin)
                .contentType("application/json")
                .content(subscriptionJson("https://example.com/webhook", "CYCLE_COUNT_APPROVED")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/webhooks/subscriptions").with(authenticatedAsAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].secret").doesNotExist());
    }

    @Test
    void given_aCreatedSubscription_when_disablingIt_then_enabledBecomesFalse() throws Exception {
        String subscriptionId = createSubscription("https://example.com/webhook", "CYCLE_COUNT_APPROVED");

        mockMvc.perform(patch("/api/v1/webhooks/subscriptions/" + subscriptionId).with(authenticatedAsAdmin)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.url", is("https://example.com/webhook")));
    }

    @Test
    void given_anUnknownSubscriptionId_when_updating_then_notFound() throws Exception {
        mockMvc.perform(patch("/api/v1/webhooks/subscriptions/" + UUID.randomUUID()).with(authenticatedAsAdmin)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("WEBHOOK_SUBSCRIPTION_NOT_FOUND")));
    }

    private String createSubscription(String url, String eventType) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/webhooks/subscriptions").with(authenticatedAsAdmin)
                        .contentType("application/json")
                        .content(subscriptionJson(url, eventType)))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String subscriptionJson(String url, String eventType) {
        return "{\"url\":\"" + url + "\",\"eventTypes\":[\"" + eventType + "\"]}";
    }
}
