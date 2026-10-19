package com.equigcc.webhook;

import com.equigcc.auth.SecretCipher;
import com.equigcc.common.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends queued deliveries. Rows are claimed with a short lease inside one transaction (so several instances can
 * share the queue), then each is sent outside any transaction and its result recorded.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final int BATCH = 20;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final WebhookSender sender;
    private final UrlGuard urlGuard;
    private final SecretCipher cipher;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final boolean enabled;

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints, WebhookSender sender,
                             UrlGuard urlGuard, SecretCipher cipher, Clock clock, PlatformTransactionManager txManager,
                             @Value("${equigcc.webhooks.enabled:true}") boolean enabled) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.sender = sender;
        this.urlGuard = urlGuard;
        this.cipher = cipher;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${equigcc.webhooks.poll-ms:15000}", initialDelayString = "${equigcc.webhooks.poll-ms:15000}")
    public void tick() {
        if (enabled) {
            dispatchDue();
        }
    }

    /** Sends whatever is due now; returns how many deliveries were attempted. */
    public int dispatchDue() {
        List<UUID> claimed = tx.execute(status -> {
            var now = clock.instant();
            var rows = deliveries.claimDue(now, BATCH);
            rows.forEach(d -> d.setNextAttemptAt(now.plus(LEASE)));
            return rows.stream().map(WebhookDelivery::getId).toList();
        });
        if (claimed == null) {
            return 0;
        }
        claimed.forEach(this::attempt);
        return claimed.size();
    }

    private void attempt(UUID deliveryId) {
        record Job(String payload, String eventType, String url, String secretEnc, boolean endpointActive) {
        }
        Job job = tx.execute(status -> deliveries.findById(deliveryId).map(d -> {
            var e = endpoints.findById(d.getEndpointId()).orElse(null);
            return e == null ? null : new Job(d.getPayload(), d.getEventType(), e.getUrl(), e.getSecretEnc(), e.isActive());
        }).orElse(null));
        if (job == null) {
            return;
        }
        WebhookSender.Result result;
        if (!job.endpointActive()) {
            result = new WebhookSender.Result(0, "Webhook is paused");
        } else {
            try {
                URI uri = urlGuard.validate(job.url());
                long ts = clock.instant().getEpochSecond();
                result = sender.post(uri, Map.of(
                        "Content-Type", "application/json",
                        "User-Agent", "EquiGCC-Webhooks/1",
                        "X-EquiGCC-Event", job.eventType(),
                        "X-EquiGCC-Delivery", deliveryId.toString(),
                        WebhookSigner.HEADER, WebhookSigner.header(cipher.decrypt(job.secretEnc()), ts, job.payload())), job.payload());
            } catch (BusinessRuleException e) {
                result = new WebhookSender.Result(0, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Webhook delivery {} failed unexpectedly: {}", deliveryId, e.toString());
                result = new WebhookSender.Result(0, "Unexpected error");
            }
        }
        WebhookSender.Result outcome = result;
        tx.executeWithoutResult(status -> deliveries.findById(deliveryId).ifPresent(d -> record(d, outcome)));
    }

    private void record(WebhookDelivery d, WebhookSender.Result r) {
        var now = clock.instant();
        d.setAttempts(d.getAttempts() + 1);
        d.setResponseCode(r.statusCode() == 0 ? null : r.statusCode());
        if (r.success()) {
            d.setStatus(DeliveryStatus.DELIVERED);
            d.setDeliveredAt(now);
            d.setLastError(null);
            return;
        }
        d.setLastError(r.error());
        var delay = RetryPolicy.nextDelay(d.getAttempts());
        if (delay.isPresent()) {
            d.setNextAttemptAt(now.plus(delay.get()));
        } else {
            d.setStatus(DeliveryStatus.FAILED);
        }
    }
}
