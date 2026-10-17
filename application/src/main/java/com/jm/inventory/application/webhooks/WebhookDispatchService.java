package com.jm.inventory.application.webhooks;

import com.jm.inventory.domain.webhooks.WebhookOutboxEvent;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The only place an outbound HTTP call to a subscription's webhook URL happens - deliberately on a
 * {@code @Scheduled} background thread, never inside the request that caused the event (see
 * docs/specs/14-webhooks/requirements.md's "Decision"). Every DB read/write is delegated to
 * {@link WebhookOutboxCoordinator} (a separate bean, each of its own short transactions);
 * this class itself holds no transaction open while {@link WebhookDeliveryClient#deliver}
 * runs, so a slow or unreachable receiver only ties up this feature's own HTTP client, never
 * a pooled JDBC connection.
 */
@Service
public class WebhookDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final WebhookOutboxCoordinator webhookOutboxCoordinator;
    private final WebhookDeliveryClient webhookDeliveryClient;
    private final int batchSize;
    private final int maxAttempts;

    public WebhookDispatchService(
            WebhookOutboxCoordinator webhookOutboxCoordinator, WebhookDeliveryClient webhookDeliveryClient,
            @Value("${app.webhooks.dispatch-batch-size:50}") int batchSize,
            @Value("${app.webhooks.max-attempts:10}") int maxAttempts) {
        this.webhookOutboxCoordinator = webhookOutboxCoordinator;
        this.webhookDeliveryClient = webhookDeliveryClient;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.webhooks.dispatch-interval-ms:15000}")
    public void dispatchDueEvents() {
        List<WebhookOutboxEvent> dueEvents = webhookOutboxCoordinator.loadDueEvents(batchSize);
        for (WebhookOutboxEvent event : dueEvents) {
            dispatchOne(event);
        }
    }

    private void dispatchOne(WebhookOutboxEvent event) {
        List<WebhookSubscription> subscriptions = webhookOutboxCoordinator.loadMatchingSubscriptions(event.getEventType());
        if (subscriptions.isEmpty()) {
            webhookOutboxCoordinator.recordDelivered(event);
            return;
        }

        String lastError = null;
        for (WebhookSubscription subscription : subscriptions) {
            try {
                webhookDeliveryClient.deliver(subscription.getUrl(), subscription.getSecret(), event.getPayloadJson());
            } catch (WebhookDeliveryException deliveryFailure) {
                lastError = deliveryFailure.getMessage();
                LOG.warn("Webhook delivery failed for event {} (subscription {}): {}",
                        event.getId(), subscription.getId(), deliveryFailure.getMessage());
            }
        }

        if (lastError == null) {
            webhookOutboxCoordinator.recordDelivered(event);
        } else {
            webhookOutboxCoordinator.recordFailedAttempt(event, lastError, maxAttempts);
        }
    }
}
