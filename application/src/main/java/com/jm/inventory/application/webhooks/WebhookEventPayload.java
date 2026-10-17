package com.jm.inventory.application.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.time.Instant;
import java.util.UUID;

/**
 * The exact wire shape delivered to a subscriber - see
 * docs/specs/14-webhooks/requirements.md's "Event payload shape". {@code eventId} is stable
 * across retries (it is the outbox row's own id), so a receiver can deduplicate.
 */
public record WebhookEventPayload(UUID eventId, WebhookEventType eventType, Instant occurredAt, Object data) {
}
