package com.jm.inventory.domain.webhooks;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class WebhookOutboxEvent {
    UUID id;
    WebhookEventType eventType;
    String payloadJson;
    WebhookOutboxStatus status;
    int attempts;
    Instant createdAt;
    Instant nextAttemptAt;
    String lastError;
}
