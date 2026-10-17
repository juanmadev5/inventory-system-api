package com.jm.inventory.application.webhooks;

/**
 * Owned here so {@link WebhookDispatchService} depends on an abstraction, never the
 * concrete HTTP/HMAC-signing mechanics - same dependency-inversion discipline as every
 * repository interface in this project.
 */
public interface WebhookDeliveryClient {

    /**
     * @throws WebhookDeliveryException on any non-2xx response, timeout, or connection
     *                                   failure - the caller treats this as "this attempt
     *                                   failed," never lets it propagate further.
     */
    void deliver(String url, String secret, String payloadJson);
}
