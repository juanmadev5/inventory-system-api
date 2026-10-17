package com.jm.inventory.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full context against a real Postgres/Redis - requires
 * {@code local-infrastructure} running (see the root README). Not an isolated unit test on
 * purpose: it is the smoke test that the multi-module wiring (component scan across
 * application/infrastructure/api, Flyway migrations, Spring Security JWT configuration)
 * actually holds together.
 */
@SpringBootTest
@ActiveProfiles("dev")
class InventoryApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
