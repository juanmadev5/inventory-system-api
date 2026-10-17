package com.jm.inventory.application.webhooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookOutboxEvent;
import com.jm.inventory.domain.webhooks.WebhookOutboxStatus;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A pure unit test - every collaborator mocked, no Spring context, no real HTTP/DB - per
 * docs/conventions/testing.md. {@link WebhookOutboxCoordinator} and
 * {@link WebhookDeliveryClient} are separate beans specifically so their
 * {@code @Transactional} methods take effect when called from here as real bean-to-bean
 * calls in production - see {@link WebhookDispatchService}'s own Javadoc - but that has no
 * bearing on testing this class's dispatch/retry decision logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatchServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private WebhookOutboxCoordinator webhookOutboxCoordinator;
    @Mock
    private WebhookDeliveryClient webhookDeliveryClient;

    private WebhookDispatchService webhookDispatchService;

    @BeforeEach
    void setUp() {
        webhookDispatchService = new WebhookDispatchService(webhookOutboxCoordinator, webhookDeliveryClient, 50, MAX_ATTEMPTS);
    }

    @Test
    void given_noMatchingSubscriptions_when_dispatching_then_recordedAsDeliveredWithoutAnyHttpCall() {
        // Given
        WebhookOutboxEvent event = anEvent();
        when(webhookOutboxCoordinator.loadDueEvents(anyInt())).thenReturn(List.of(event));
        when(webhookOutboxCoordinator.loadMatchingSubscriptions(event.getEventType()))
                .thenReturn(List.of());

        // When
        webhookDispatchService.dispatchDueEvents();

        // Then
        verify(webhookDeliveryClient, never()).deliver(any(), any(), any());
        verify(webhookOutboxCoordinator).recordDelivered(event);
    }

    @Test
    void given_oneMatchingSubscriptionDeliversSuccessfully_when_dispatching_then_recordedAsDelivered() {
        // Given
        WebhookOutboxEvent event = anEvent();
        WebhookSubscription subscription = aSubscription(event.getEventType());
        when(webhookOutboxCoordinator.loadDueEvents(anyInt())).thenReturn(List.of(event));
        when(webhookOutboxCoordinator.loadMatchingSubscriptions(event.getEventType()))
                .thenReturn(List.of(subscription));

        // When
        webhookDispatchService.dispatchDueEvents();

        // Then
        verify(webhookDeliveryClient).deliver(subscription.getUrl(), subscription.getSecret(), event.getPayloadJson());
        verify(webhookOutboxCoordinator).recordDelivered(event);
    }

    @Test
    void given_deliveryFails_when_dispatching_then_recordedAsFailedAttemptWithMaxAttempts() {
        // Given
        WebhookOutboxEvent event = anEvent();
        WebhookSubscription subscription = aSubscription(event.getEventType());
        when(webhookOutboxCoordinator.loadDueEvents(anyInt())).thenReturn(List.of(event));
        when(webhookOutboxCoordinator.loadMatchingSubscriptions(event.getEventType()))
                .thenReturn(List.of(subscription));
        org.mockito.Mockito.doThrow(new WebhookDeliveryException("connection refused", null))
                .when(webhookDeliveryClient).deliver(any(), any(), any());

        // When
        webhookDispatchService.dispatchDueEvents();

        // Then
        verify(webhookOutboxCoordinator, never()).recordDelivered(any());
        verify(webhookOutboxCoordinator).recordFailedAttempt(eq(event), eq("connection refused"), eq(MAX_ATTEMPTS));
    }

    @Test
    void given_twoSubscriptionsOneFailsOneSucceeds_when_dispatching_then_recordedAsFailedAttempt() {
        // Given - every matching subscription gets re-POSTed, even the one that already succeeded
        WebhookOutboxEvent event = anEvent();
        WebhookSubscription succeedingSubscription = aSubscription(event.getEventType());
        WebhookSubscription failingSubscription = aSubscription(event.getEventType());
        when(webhookOutboxCoordinator.loadDueEvents(anyInt())).thenReturn(List.of(event));
        when(webhookOutboxCoordinator.loadMatchingSubscriptions(event.getEventType()))
                .thenReturn(List.of(succeedingSubscription, failingSubscription));
        org.mockito.Mockito.doNothing()
                .when(webhookDeliveryClient)
                .deliver(succeedingSubscription.getUrl(), succeedingSubscription.getSecret(), event.getPayloadJson());
        org.mockito.Mockito.doThrow(new WebhookDeliveryException("timeout", null))
                .when(webhookDeliveryClient)
                .deliver(failingSubscription.getUrl(), failingSubscription.getSecret(), event.getPayloadJson());

        // When
        webhookDispatchService.dispatchDueEvents();

        // Then
        verify(webhookDeliveryClient).deliver(
                succeedingSubscription.getUrl(), succeedingSubscription.getSecret(), event.getPayloadJson());
        verify(webhookOutboxCoordinator, never()).recordDelivered(any());
        verify(webhookOutboxCoordinator).recordFailedAttempt(eq(event), eq("timeout"), eq(MAX_ATTEMPTS));
    }

    private WebhookOutboxEvent anEvent() {
        Instant now = Instant.now();
        return new WebhookOutboxEvent(
                UUID.randomUUID(), WebhookEventType.CYCLE_COUNT_APPROVED, "{\"eventId\":\"x\"}",
                WebhookOutboxStatus.PENDING, 0, now, now, null);
    }

    private WebhookSubscription aSubscription(WebhookEventType eventType) {
        UUID subscriptionId = UUID.randomUUID();
        return new WebhookSubscription(
                subscriptionId, "https://example.com/webhook/" + subscriptionId,
                "secret-" + subscriptionId, Set.of(eventType), true);
    }
}
