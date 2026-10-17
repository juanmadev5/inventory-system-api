package com.jm.inventory.application.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import java.util.Set;
import java.util.UUID;

/**
 * What gets audited for a subscription write - everything except {@code secret}, which must
 * never land in an audit entry (same rule as the temporary password in
 * docs/specs/11-user-management/requirements.md).
 */
public record WebhookSubscriptionSnapshot(UUID id, String url, Set<WebhookEventType> eventTypes, boolean enabled) {

    public static WebhookSubscriptionSnapshot from(WebhookSubscription subscription) {
        return new WebhookSubscriptionSnapshot(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypes(), subscription.isEnabled());
    }
}
