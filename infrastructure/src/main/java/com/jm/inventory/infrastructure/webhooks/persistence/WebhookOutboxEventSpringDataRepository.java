package com.jm.inventory.infrastructure.webhooks.persistence;

import com.jm.inventory.domain.webhooks.WebhookOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookOutboxEventSpringDataRepository extends JpaRepository<WebhookOutboxEventJpaEntity, UUID> {

    List<WebhookOutboxEventJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            WebhookOutboxStatus status, Instant nextAttemptAt, Pageable pageable);
}
