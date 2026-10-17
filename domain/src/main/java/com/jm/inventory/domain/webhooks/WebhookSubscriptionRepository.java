package com.jm.inventory.domain.webhooks;

import com.jm.inventory.domain.common.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository {

    WebhookSubscription save(WebhookSubscription subscription);

    Optional<WebhookSubscription> findById(UUID id);

    PagedResult<WebhookSubscription> findAll(int page, int pageSize);

    List<WebhookSubscription> findEnabledByEventType(WebhookEventType eventType);
}
