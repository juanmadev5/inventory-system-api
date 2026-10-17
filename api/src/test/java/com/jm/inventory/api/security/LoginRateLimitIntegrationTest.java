package com.jm.inventory.api.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.infrastructure.platform.RateLimitKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link LoginRateLimitFilter} against the real Redis from
 * {@code local-infrastructure} (no Testcontainers Redis module - this is the one place in
 * the suite that needs a real {@code INCR}/{@code EXPIRE} round trip, same as the cache
 * tests). The rate limit runs ahead of authentication, so a wrong password still counts
 * against it and gets 401 while under the limit, 429 once over it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class LoginRateLimitIntegrationTest {

    private static final int MAX_REQUESTS = 2;
    private static final String REMOTE_ADDR = "127.0.0.1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "simple");
        registry.add("app.rate-limit.login.max-requests", () -> MAX_REQUESTS);
        registry.add("app.rate-limit.login.window-seconds", () -> 60);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRateLimitCounter() {
        redisTemplate.delete(RateLimitKeys.LOGIN_PREFIX + REMOTE_ADDR);
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(REMOTE_ADDR);
                    return request;
                })
                .contentType("application/json")
                .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"));
    }

    @Test
    void given_requestsUnderTheLimit_when_loggingIn_then_rateLimiterLetsThemThrough() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            login().andExpect(status().isUnauthorized());
        }
    }

    @Test
    void given_requestsOverTheLimit_when_loggingIn_then_tooManyRequests() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            login().andExpect(status().isUnauthorized());
        }

        login()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("RATE_LIMIT_EXCEEDED")));
    }
}
