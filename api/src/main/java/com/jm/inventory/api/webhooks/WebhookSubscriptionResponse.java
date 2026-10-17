package com.jm.inventory.api.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import java.util.Set;
import java.util.UUID;

public record WebhookSubscriptionResponse(UUID id, String url, Set<WebhookEventType> eventTypes, boolean enabled) {

    public static WebhookSubscriptionResponse from(WebhookSubscription subscription) {
        return new WebhookSubscriptionResponse(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypes(), subscription.isEnabled());
    }
}
