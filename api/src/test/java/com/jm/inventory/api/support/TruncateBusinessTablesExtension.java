package com.jm.inventory.api.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Wipes every business table before each test method. Without per-tenant isolation (see
 * docs/security.md), a {@code @SpringBootTest} class that shares one Testcontainers
 * Postgres across all its {@code @Test} methods would otherwise leak a row created by one
 * test (e.g. a category named "Electronics") into the next, causing spurious uniqueness
 * conflicts. {@code users} is deliberately excluded - the handful of tests that exercise
 * real login/user-management flows manage their own rows with per-test-unique emails.
 */
public class TruncateBusinessTablesExtension implements BeforeEachCallback {

    private static final String BUSINESS_TABLES = String.join(", ",
            "categories", "products", "warehouses", "stock_levels", "stock_movements",
            "suppliers", "purchase_orders", "purchase_order_lines", "reorder_thresholds",
            "customers", "sales_orders", "sales_order_lines", "cycle_counts", "cycle_count_lines",
            "audit_log_entries", "webhook_subscriptions", "webhook_subscription_event_types",
            "webhook_outbox_events");

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(applicationContext.getBean(DataSource.class));
        jdbcTemplate.execute("TRUNCATE TABLE " + BUSINESS_TABLES + " RESTART IDENTITY CASCADE");
    }
}
