package com.jm.inventory.domain.webhooks;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class WebhookSubscriptionNotFoundException extends NotFoundException {

    public WebhookSubscriptionNotFoundException(UUID id) {
        super("Webhook subscription " + id + " not found");
    }

    @Override
    public String code() {
        return "WEBHOOK_SUBSCRIPTION_NOT_FOUND";
    }
}
