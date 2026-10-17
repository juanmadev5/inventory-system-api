package com.jm.inventory.application.webhooks;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import com.jm.inventory.domain.webhooks.WebhookSubscriptionNotFoundException;
import com.jm.inventory.domain.webhooks.WebhookSubscriptionRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final AuditLogService auditLogService;

    public WebhookSubscriptionService(
            WebhookSubscriptionRepository webhookSubscriptionRepository, AuditLogService auditLogService) {
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
        this.auditLogService = auditLogService;
    }

    public WebhookSubscription create(String url, Set<WebhookEventType> eventTypes) {
        WebhookSubscription created = webhookSubscriptionRepository.save(new WebhookSubscription(
                UUID.randomUUID(), url, WebhookSecretGenerator.generate(), eventTypes, true));
        auditLogService.recordCreated(
                AuditEntityType.WEBHOOK_SUBSCRIPTION, created.getId(), WebhookSubscriptionSnapshot.from(created));
        return created;
    }

    @Transactional(readOnly = true)
    public PagedResult<WebhookSubscription> list(Integer page, Integer pageSize) {
        return webhookSubscriptionRepository.findAll(
                PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    public WebhookSubscription update(UUID id, String url, Set<WebhookEventType> eventTypes, Boolean enabled) {
        WebhookSubscription existing = requireExisting(id);
        WebhookSubscription updated = new WebhookSubscription(
                existing.getId(),
                url != null ? url : existing.getUrl(),
                existing.getSecret(),
                eventTypes != null ? eventTypes : existing.getEventTypes(),
                enabled != null ? enabled : existing.isEnabled());
        WebhookSubscription saved = webhookSubscriptionRepository.save(updated);
        auditLogService.recordUpdated(
                AuditEntityType.WEBHOOK_SUBSCRIPTION, id,
                WebhookSubscriptionSnapshot.from(existing), WebhookSubscriptionSnapshot.from(saved));
        return saved;
    }

    private WebhookSubscription requireExisting(UUID id) {
        return webhookSubscriptionRepository.findById(id)
                .orElseThrow(() -> new WebhookSubscriptionNotFoundException(id));
    }
}
