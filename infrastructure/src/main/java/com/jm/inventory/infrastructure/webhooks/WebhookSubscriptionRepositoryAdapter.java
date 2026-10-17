package com.jm.inventory.infrastructure.webhooks;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import com.jm.inventory.domain.webhooks.WebhookSubscriptionRepository;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookSubscriptionJpaEntity;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookSubscriptionSpringDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class WebhookSubscriptionRepositoryAdapter implements WebhookSubscriptionRepository {

    private final WebhookSubscriptionSpringDataRepository springDataRepository;

    public WebhookSubscriptionRepositoryAdapter(WebhookSubscriptionSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public WebhookSubscription save(WebhookSubscription subscription) {
        WebhookSubscriptionJpaEntity entity = springDataRepository.findById(subscription.getId())
                .map(existing -> applyTo(existing, subscription))
                .orElseGet(() -> toNewEntity(subscription));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<WebhookSubscription> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PagedResult<WebhookSubscription> findAll(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt"));
        Page<WebhookSubscriptionJpaEntity> result = springDataRepository.findAllBy(pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    @Override
    public List<WebhookSubscription> findEnabledByEventType(WebhookEventType eventType) {
        return springDataRepository.findByEnabledTrueAndEventTypesContaining(eventType).stream()
                .map(this::toDomain)
                .toList();
    }

    private WebhookSubscriptionJpaEntity applyTo(WebhookSubscriptionJpaEntity entity, WebhookSubscription subscription) {
        entity.setUrl(subscription.getUrl());
        entity.setSecret(subscription.getSecret());
        entity.setEventTypes(subscription.getEventTypes());
        entity.setEnabled(subscription.isEnabled());
        return entity;
    }

    private WebhookSubscriptionJpaEntity toNewEntity(WebhookSubscription subscription) {
        return WebhookSubscriptionJpaEntity.builder()
                .id(subscription.getId())
                .url(subscription.getUrl())
                .secret(subscription.getSecret())
                .eventTypes(subscription.getEventTypes())
                .enabled(subscription.isEnabled())
                .createdAt(Instant.now())
                .build();
    }

    private WebhookSubscription toDomain(WebhookSubscriptionJpaEntity entity) {
        return new WebhookSubscription(
                entity.getId(), entity.getUrl(), entity.getSecret(), entity.getEventTypes(), entity.isEnabled());
    }
}
