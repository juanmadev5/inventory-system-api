package com.jm.inventory.api.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import java.util.Set;
import java.util.UUID;

/**
 * {@code secret} is present only here, on the create response - once, never retrievable
 * again, same rule as the temporary password in
 * docs/specs/11-user-management/requirements.md.
 */
public record CreatedWebhookSubscriptionResponse(
        UUID id, String url, Set<WebhookEventType> eventTypes, boolean enabled, String secret) {

    public static CreatedWebhookSubscriptionResponse from(WebhookSubscription subscription) {
        return new CreatedWebhookSubscriptionResponse(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypes(), subscription.isEnabled(),
                subscription.getSecret());
    }
}
