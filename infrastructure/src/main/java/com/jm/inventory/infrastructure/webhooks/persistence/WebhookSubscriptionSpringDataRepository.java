package com.jm.inventory.infrastructure.webhooks.persistence;

import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookSubscriptionSpringDataRepository extends JpaRepository<WebhookSubscriptionJpaEntity, UUID> {

    Page<WebhookSubscriptionJpaEntity> findAllBy(Pageable pageable);

    List<WebhookSubscriptionJpaEntity> findByEnabledTrueAndEventTypesContaining(WebhookEventType eventType);
}
