package com.jm.inventory.application.webhooks;

public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
