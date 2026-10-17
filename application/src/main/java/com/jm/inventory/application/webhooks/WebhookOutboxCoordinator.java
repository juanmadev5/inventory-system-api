package com.jm.inventory.application.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookOutboxEvent;
import com.jm.inventory.domain.webhooks.WebhookOutboxRepository;
import com.jm.inventory.domain.webhooks.WebhookOutboxStatus;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import com.jm.inventory.domain.webhooks.WebhookSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The DB-touching half of dispatch, kept as its own bean (never called via self-invocation
 * from {@link WebhookDispatchService}) so each method's {@code @Transactional} actually
 * takes effect - a private method call on the same instance never goes through the Spring
 * AOP proxy that makes the annotation do anything. Every method here is short and never
 * wraps the HTTP delivery call itself - see {@link WebhookDispatchService}'s Javadoc for why
 * that must happen with no transaction open.
 */
@Service
public class WebhookOutboxCoordinator {

    private static final long BASE_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 3600;
    private static final int MAX_BACKOFF_SHIFT = 10;

    private final WebhookOutboxRepository webhookOutboxRepository;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;

    public WebhookOutboxCoordinator(
            WebhookOutboxRepository webhookOutboxRepository, WebhookSubscriptionRepository webhookSubscriptionRepository) {
        this.webhookOutboxRepository = webhookOutboxRepository;
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<WebhookOutboxEvent> loadDueEvents(int limit) {
        return webhookOutboxRepository.findDuePending(Instant.now(), limit);
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscription> loadMatchingSubscriptions(WebhookEventType eventType) {
        return webhookSubscriptionRepository.findEnabledByEventType(eventType);
    }

    @Transactional
    public void recordDelivered(WebhookOutboxEvent event) {
        webhookOutboxRepository.save(new WebhookOutboxEvent(
                event.getId(), event.getEventType(), event.getPayloadJson(),
                WebhookOutboxStatus.DELIVERED, event.getAttempts(), event.getCreatedAt(), event.getNextAttemptAt(), null));
    }

    @Transactional
    public void recordFailedAttempt(WebhookOutboxEvent event, String error, int maxAttempts) {
        int attempts = event.getAttempts() + 1;
        boolean exhausted = attempts >= maxAttempts;
        Instant nextAttemptAt = Instant.now().plusSeconds(backoffSeconds(attempts));
        webhookOutboxRepository.save(new WebhookOutboxEvent(
                event.getId(), event.getEventType(), event.getPayloadJson(),
                exhausted ? WebhookOutboxStatus.FAILED : WebhookOutboxStatus.PENDING, attempts, event.getCreatedAt(),
                nextAttemptAt, error));
    }

    private long backoffSeconds(int attempts) {
        return Math.min(BASE_BACKOFF_SECONDS * (1L << Math.min(attempts, MAX_BACKOFF_SHIFT)), MAX_BACKOFF_SECONDS);
    }
}
