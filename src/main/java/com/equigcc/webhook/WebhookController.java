package com.equigcc.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Integrations")
public class WebhookController {

    private static final String FOUNDER_ONLY = "hasRole('FOUNDER') and @orgAccess.canAccess(authentication, #orgId)";

    public record CreateRequest(@NotBlank @Size(max = 2000) String url, @NotEmpty List<String> events) {
    }

    public record UpdateRequest(@Size(max = 2000) String url, List<String> events, Boolean active) {
    }

    public record View(UUID id, String url, List<String> events, boolean active, String createdBy, Instant createdAt) {
        static View from(WebhookEndpoint e) {
            return new View(e.getId(), e.getUrl(), e.eventNames(), e.isActive(), e.getCreatedBy(), e.getCreatedAt());
        }
    }

    /** {@code secret} signs every delivery and is returned only here. */
    public record CreatedView(View webhook, String secret) {
    }

    public record DeliveryView(UUID id, String eventType, DeliveryStatus status, int attempts, Integer responseCode,
                               String lastError, Instant createdAt, Instant nextAttemptAt, Instant deliveredAt) {
        static DeliveryView from(WebhookDelivery d) {
            return new DeliveryView(d.getId(), d.getEventType(), d.getStatus(), d.getAttempts(), d.getResponseCode(),
                    d.getLastError(), d.getCreatedAt(), d.getNextAttemptAt(), d.getDeliveredAt());
        }
    }

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @Operation(summary = "Event names a webhook can subscribe to")
    @GetMapping("/webhook-events")
    @PreAuthorize(FOUNDER_ONLY)
    public List<String> events(@PathVariable UUID orgId) {
        return Arrays.stream(WebhookEvent.values()).filter(e -> e != WebhookEvent.PING).map(WebhookEvent::wireName).toList();
    }

    @Operation(summary = "Add a webhook",
            description = "Each delivery is a JSON POST signed with X-EquiGCC-Signature (t=<seconds>,v1=<HMAC-SHA256 of 't.body'>). "
                    + "The signing secret is returned once.")
    @PostMapping("/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(FOUNDER_ONLY)
    public CreatedView create(@PathVariable UUID orgId, @Valid @RequestBody CreateRequest r, Authentication auth) {
        var created = service.create(orgId, r.url(), r.events(), auth.getName());
        return new CreatedView(View.from(created.endpoint()), created.secret());
    }

    @GetMapping("/webhooks")
    @PreAuthorize(FOUNDER_ONLY)
    public List<View> list(@PathVariable UUID orgId) {
        return service.list(orgId).stream().map(View::from).toList();
    }

    @PatchMapping("/webhooks/{id}")
    @PreAuthorize(FOUNDER_ONLY)
    public View update(@PathVariable UUID orgId, @PathVariable UUID id, @Valid @RequestBody UpdateRequest r, Authentication auth) {
        return View.from(service.update(orgId, id, r.url(), r.events(), r.active(), auth.getName()));
    }

    @DeleteMapping("/webhooks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(FOUNDER_ONLY)
    public void delete(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        service.delete(orgId, id, auth.getName());
    }

    @Operation(summary = "Queue a test event to this webhook")
    @PostMapping("/webhooks/{id}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(FOUNDER_ONLY)
    public DeliveryView test(@PathVariable UUID orgId, @PathVariable UUID id) {
        return DeliveryView.from(service.sendTest(orgId, id));
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @PreAuthorize(FOUNDER_ONLY)
    public List<DeliveryView> deliveries(@PathVariable UUID orgId, @PathVariable UUID id,
                                         @RequestParam(defaultValue = "30") int limit) {
        return service.deliveries(orgId, id, limit).stream().map(DeliveryView::from).toList();
    }

    @Operation(summary = "Send a failed or waiting delivery again")
    @PostMapping("/webhook-deliveries/{deliveryId}/retry")
    @PreAuthorize(FOUNDER_ONLY)
    public DeliveryView retry(@PathVariable UUID orgId, @PathVariable UUID deliveryId) {
        return DeliveryView.from(service.retry(orgId, deliveryId));
    }
}
