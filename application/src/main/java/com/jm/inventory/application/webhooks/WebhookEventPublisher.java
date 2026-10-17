package com.jm.inventory.application.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookOutboxEvent;
import com.jm.inventory.domain.webhooks.WebhookOutboxRepository;
import com.jm.inventory.domain.webhooks.WebhookOutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Called by the four trigger points (see docs/specs/14-webhooks/requirements.md) right after
 * the business state change that caused the event, inside the *same* transaction - never a
 * {@code @Transactional} boundary of its own, so it always joins whatever transaction the
 * caller is already in and commits atomically with it. No HTTP call happens here; that is
 * {@code WebhookDispatchService}'s job, strictly later and out of band.
 */
@Service
public class WebhookEventPublisher {

    private final WebhookOutboxRepository webhookOutboxRepository;
    private final ObjectMapper objectMapper;

    public WebhookEventPublisher(WebhookOutboxRepository webhookOutboxRepository, ObjectMapper objectMapper) {
        this.webhookOutboxRepository = webhookOutboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(WebhookEventType eventType, Object data) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String payloadJson = objectMapper.writeValueAsString(new WebhookEventPayload(eventId, eventType, now, data));
        webhookOutboxRepository.save(new WebhookOutboxEvent(
                eventId, eventType, payloadJson, WebhookOutboxStatus.PENDING, 0, now, now, null));
    }
}
