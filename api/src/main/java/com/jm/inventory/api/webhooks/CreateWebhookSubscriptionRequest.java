package com.jm.inventory.api.webhooks;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateWebhookSubscriptionRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotEmpty Set<WebhookEventType> eventTypes) {
}
