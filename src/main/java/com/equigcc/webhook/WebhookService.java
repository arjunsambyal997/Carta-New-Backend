package com.equigcc.webhook;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.auth.SecretCipher;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manages endpoints and writes events to the outbox; {@link WebhookDispatcher} does the sending. */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    static final int MAX_ENDPOINTS = 10;
    private static final String ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public record Created(WebhookEndpoint endpoint, String secret) {
    }

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final UrlGuard urlGuard;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    public WebhookService(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries, UrlGuard urlGuard,
                          SecretCipher cipher, AuditService audit, ObjectMapper json, Clock clock,
                          @Value("${equigcc.webhooks.enabled:true}") boolean enabled) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.urlGuard = urlGuard;
        this.cipher = cipher;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional
    public Created create(UUID orgId, String url, List<String> events, String actor) {
        urlGuard.validate(url);
        List<String> names = validatedEvents(events);
        if (endpoints.countByOrganizationId(orgId) >= MAX_ENDPOINTS) {
            throw new BusinessRuleException("An organization can have at most " + MAX_ENDPOINTS + " webhooks; delete one first");
        }
        String secret = "whsec_" + randomString(32);
        WebhookEndpoint saved = endpoints.save(new WebhookEndpoint(orgId, url.trim(), cipher.encrypt(secret), names, actor));
        audit.record(orgId, "WebhookEndpoint", saved.getId(), AuditAction.CREATE, "Webhook added: " + saved.getUrl(), actor);
        return new Created(saved, secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> list(UUID orgId) {
        return endpoints.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public WebhookEndpoint update(UUID orgId, UUID id, String url, List<String> events, Boolean active, String actor) {
        WebhookEndpoint e = find(orgId, id);
        if (url != null) {
            urlGuard.validate(url);
            e.setUrl(url.trim());
        }
        if (events != null) {
            e.setEventNames(validatedEvents(events));
        }
        if (active != null) {
            e.setActive(active);
        }
        audit.record(orgId, "WebhookEndpoint", id, AuditAction.UPDATE, "Webhook updated: " + e.getUrl() + (e.isActive() ? "" : " (paused)"), actor);
        return e;
    }

    @Transactional
    public void delete(UUID orgId, UUID id, String actor) {
        WebhookEndpoint e = find(orgId, id);
        endpoints.delete(e);
        audit.record(orgId, "WebhookEndpoint", id, AuditAction.DELETE, "Webhook removed: " + e.getUrl(), actor);
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> deliveries(UUID orgId, UUID endpointId, int limit) {
        find(orgId, endpointId);
        return deliveries.findByEndpointIdOrderByCreatedAtDesc(endpointId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    /** Queues a ping to one endpoint so its owner can check the receiving side. */
    @Transactional
    public WebhookDelivery sendTest(UUID orgId, UUID endpointId) {
        WebhookEndpoint e = find(orgId, endpointId);
        return enqueue(e, WebhookEvent.PING, Map.of("message", "This is a test event from Sijill."));
    }

    @Transactional
    public WebhookDelivery retry(UUID orgId, UUID deliveryId) {
        WebhookDelivery d = deliveries.findById(deliveryId).filter(x -> x.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        if (d.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException("That delivery already went through");
        }
        d.setStatus(DeliveryStatus.PENDING);
        d.setAttempts(0);
        d.setNextAttemptAt(clock.instant());
        return d;
    }

    /**
     * Queues an event for every active endpoint that subscribed to it. Never throws: a webhook problem must not
     * undo the register change that caused the event.
     */
    @Transactional
    public void publish(UUID orgId, WebhookEvent event, Map<String, Object> data) {
        if (!enabled) {
            return;
        }
        try {
            for (WebhookEndpoint e : endpoints.findByOrganizationIdAndActiveTrue(orgId)) {
                if (e.subscribesTo(event)) {
                    enqueue(e, event, data);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not queue webhook event {} for {}: {}", event.wireName(), orgId, ex.toString());
        }
    }

    private WebhookDelivery enqueue(WebhookEndpoint endpoint, WebhookEvent event, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("type", event.wireName());
        envelope.put("createdAt", clock.instant().toString());
        envelope.put("organizationId", endpoint.getOrganizationId().toString());
        envelope.put("data", data);
        try {
            return deliveries.save(new WebhookDelivery(endpoint.getId(), endpoint.getOrganizationId(), event.wireName(),
                    json.writeValueAsString(envelope), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebhookEndpoint find(UUID orgId, UUID id) {
        return endpoints.findById(id).filter(e -> e.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Webhook not found"));
    }

    private static List<String> validatedEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            throw new BusinessRuleException("Choose at least one event");
        }
        return events.stream().map(String::trim).distinct().map(n -> {
            var parsed = WebhookEvent.parse(n);
            if (parsed.isEmpty() || parsed.get() == WebhookEvent.PING) {
                throw new BusinessRuleException("Unknown event: " + n);
            }
            return n;
        }).toList();
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
