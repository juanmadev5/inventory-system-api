package com.jm.inventory.domain.webhooks;

import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class WebhookSubscription {
    UUID id;
    String url;
    String secret;
    Set<WebhookEventType> eventTypes;
    boolean enabled;
}
