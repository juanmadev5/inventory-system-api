package com.jm.inventory.infrastructure.webhooks;

import com.jm.inventory.domain.webhooks.WebhookOutboxEvent;
import com.jm.inventory.domain.webhooks.WebhookOutboxRepository;
import com.jm.inventory.domain.webhooks.WebhookOutboxStatus;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookOutboxEventJpaEntity;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookOutboxEventSpringDataRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class WebhookOutboxRepositoryAdapter implements WebhookOutboxRepository {

    private final WebhookOutboxEventSpringDataRepository springDataRepository;

    public WebhookOutboxRepositoryAdapter(WebhookOutboxEventSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public WebhookOutboxEvent save(WebhookOutboxEvent event) {
        WebhookOutboxEventJpaEntity entity = springDataRepository.findById(event.getId())
                .map(existing -> applyTo(existing, event))
                .orElseGet(() -> toNewEntity(event));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public List<WebhookOutboxEvent> findDuePending(Instant now, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return springDataRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(WebhookOutboxStatus.PENDING, now, pageable)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private WebhookOutboxEventJpaEntity applyTo(WebhookOutboxEventJpaEntity entity, WebhookOutboxEvent event) {
        entity.setStatus(event.getStatus());
        entity.setAttempts(event.getAttempts());
        entity.setNextAttemptAt(event.getNextAttemptAt());
        entity.setLastError(event.getLastError());
        return entity;
    }

    private WebhookOutboxEventJpaEntity toNewEntity(WebhookOutboxEvent event) {
        return WebhookOutboxEventJpaEntity.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .payload(event.getPayloadJson())
                .status(event.getStatus())
                .attempts(event.getAttempts())
                .createdAt(event.getCreatedAt())
                .nextAttemptAt(event.getNextAttemptAt())
                .lastError(event.getLastError())
                .build();
    }

    private WebhookOutboxEvent toDomain(WebhookOutboxEventJpaEntity entity) {
        return new WebhookOutboxEvent(
                entity.getId(), entity.getEventType(), entity.getPayload(), entity.getStatus(),
                entity.getAttempts(), entity.getCreatedAt(), entity.getNextAttemptAt(), entity.getLastError());
    }
}
