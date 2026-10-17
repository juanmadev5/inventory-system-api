package com.jm.inventory.api.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.domain.user.Role;
import com.jm.inventory.infrastructure.platform.RateLimitKeys;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link ApiRateLimitFilter} against the real Redis from
 * {@code local-infrastructure} - keyed by the caller's own user id (the JWT {@code sub}
 * claim), unlike {@link LoginRateLimitFilter}'s per-IP key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ApiRateLimitIntegrationTest {

    private static final int MAX_REQUESTS = 2;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "simple");
        registry.add("app.rate-limit.api-wide.max-requests", () -> MAX_REQUESTS);
        registry.add("app.rate-limit.api-wide.window-seconds", () -> 60);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String userId;
    private JwtRequestPostProcessor authenticatedAsStaff;

    @BeforeEach
    void clearRateLimitCounter() {
        userId = UUID.randomUUID().toString();
        authenticatedAsStaff = jwt().jwt(builder -> builder.subject(userId))
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
        redisTemplate.delete(RateLimitKeys.API_WIDE_PREFIX + userId);
    }

    private ResultActions listCategories() throws Exception {
        return mockMvc.perform(get("/api/v1/categories").with(authenticatedAsStaff));
    }

    @Test
    void given_requestsUnderTheLimit_when_listingCategories_then_rateLimiterLetsThemThrough() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            listCategories().andExpect(status().isOk());
        }
    }

    @Test
    void given_requestsOverTheLimit_when_listingCategories_then_tooManyRequests() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            listCategories().andExpect(status().isOk());
        }

        listCategories()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("RATE_LIMIT_EXCEEDED")));
    }

    @Test
    void given_twoDifferentUsers_when_oneExceedsItsLimit_then_theOtherIsUnaffected() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            listCategories().andExpect(status().isOk());
        }
        listCategories().andExpect(status().isTooManyRequests());

        clearRateLimitCounter();
        listCategories().andExpect(status().isOk());
    }
}
