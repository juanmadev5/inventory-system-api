package com.jm.inventory.api.webhooks;

import com.jm.inventory.api.security.RequireAdmin;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.webhooks.WebhookSubscriptionService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.webhooks.WebhookSubscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code ADMIN}-only on every method - delivery itself needs no endpoint here, see
 * docs/specs/14-webhooks/requirements.md.
 */
@RestController
@RequestMapping("/api/v1/webhooks/subscriptions")
@Tag(name = "Webhooks")
@RequireAdmin
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    public WebhookSubscriptionController(WebhookSubscriptionService webhookSubscriptionService) {
        this.webhookSubscriptionService = webhookSubscriptionService;
    }

    @GetMapping
    @Operation(summary = "List webhook subscriptions.")
    @ApiResponse(responseCode = "200", description = "Paginated list")
    public PageResponse<WebhookSubscriptionResponse> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResult<WebhookSubscription> result = webhookSubscriptionService.list(page, pageSize);
        return PageResponse.of(
                result.items().stream().map(WebhookSubscriptionResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @Operation(summary = "Create a webhook subscription.")
    @ApiResponse(responseCode = "201", description = "Created - secret is present only on this response, once")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<CreatedWebhookSubscriptionResponse> create(
            @Valid @RequestBody CreateWebhookSubscriptionRequest request) {
        WebhookSubscription created = webhookSubscriptionService.create(request.url(), request.eventTypes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(CreatedWebhookSubscriptionResponse.from(created));
    }

    @PatchMapping("/{subscriptionId}")
    @Operation(summary = "Partially update a subscription - url, eventTypes, and/or enabled.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "WEBHOOK_SUBSCRIPTION_NOT_FOUND")
    public WebhookSubscriptionResponse update(
            @PathVariable UUID subscriptionId, @RequestBody UpdateWebhookSubscriptionRequest request) {
        WebhookSubscription updated = webhookSubscriptionService.update(
                subscriptionId, request.url(), request.eventTypes(), request.enabled());
        return WebhookSubscriptionResponse.from(updated);
    }
}
