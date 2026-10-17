package com.jm.inventory.infrastructure.webhooks;

import com.jm.inventory.application.webhooks.WebhookDeliveryClient;
import com.jm.inventory.application.webhooks.WebhookDeliveryException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HMAC-SHA256-signs every delivery with the subscription's own secret - see
 * docs/specs/14-webhooks/requirements.md's "Decision" for why (no TLS client certs or IP
 * allowlisting needed for a receiver to verify authenticity). Both connect and read
 * timeouts are short and configurable (`app.webhooks.delivery-timeout-ms`) - a slow or
 * unreachable receiver must never block the dispatcher thread for long, let alone hold a
 * database connection (it holds none at all during this call - see
 * {@code WebhookDispatchService}).
 */
@Component
public class RestClientWebhookDeliveryClient implements WebhookDeliveryClient {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;

    public RestClientWebhookDeliveryClient(@Value("${app.webhooks.delivery-timeout-ms:5000}") long timeoutMs) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofMillis(timeoutMs))
                .withReadTimeout(Duration.ofMillis(timeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void deliver(String url, String secret, String payloadJson) {
        String signature = sign(secret, payloadJson);
        try {
            restClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(SIGNATURE_HEADER, "sha256=" + signature)
                    .body(payloadJson)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException deliveryFailure) {
            throw new WebhookDeliveryException("Delivery to " + url + " failed: " + deliveryFailure.getMessage(), deliveryFailure);
        }
    }

    private String sign(String secret, String payloadJson) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signatureBytes = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 must always be available on the JVM", impossible);
        }
    }
}
