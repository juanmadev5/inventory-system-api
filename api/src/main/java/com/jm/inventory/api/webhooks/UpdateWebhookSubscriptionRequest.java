package com.jm.inventory.api.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateWebhookSubscriptionRequest(
        @Size(max = 2048) String url,
        Set<WebhookEventType> eventTypes,
        Boolean enabled) {
}
