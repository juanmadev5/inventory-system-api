package com.jm.inventory.domain.webhooks;

import java.time.Instant;
import java.util.List;

public interface WebhookOutboxRepository {

    WebhookOutboxEvent save(WebhookOutboxEvent event);

    /**
     * {@code PENDING} rows due for an attempt ({@code nextAttemptAt <= now}), oldest first,
     * bounded by {@code limit} so one tick never scans the whole table.
     */
    List<WebhookOutboxEvent> findDuePending(Instant now, int limit);
}
