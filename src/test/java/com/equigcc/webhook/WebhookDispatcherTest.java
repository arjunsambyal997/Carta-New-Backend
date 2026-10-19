package com.equigcc.webhook;

import com.equigcc.auth.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final List<Map<String, String>> sentHeaders = new ArrayList<>();
    private WebhookSender.Result nextResult = new WebhookSender.Result(200, null);
    private WebhookEndpoint endpoint;
    private WebhookDelivery delivery;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        UUID org = UUID.randomUUID();
        endpoint = new WebhookEndpoint(org, "https://example.com/hook", "enc", List.of("certificate.issued"), "owner");
        endpoint.setId(UUID.randomUUID());
        delivery = new WebhookDelivery(endpoint.getId(), org, "certificate.issued", "{\"type\":\"certificate.issued\"}", NOW);
        delivery.setId(UUID.randomUUID());
        when(deliveries.claimDue(NOW, 20)).thenReturn(List.of(delivery));
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(endpoints.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(cipher.decrypt("enc")).thenReturn("whsec_secret");
        WebhookSender sender = (uri, headers, body) -> {
            sentHeaders.add(headers);
            return nextResult;
        };
        dispatcher = new WebhookDispatcher(deliveries, endpoints, sender, new UrlGuard(true), cipher,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class), true);
    }

    @Test
    void successfulSendMarksDeliveredAndSignsTheBody() {
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getResponseCode()).isEqualTo(200);
        var headers = sentHeaders.get(0);
        assertThat(headers).containsEntry("X-EquiGCC-Event", "certificate.issued");
        assertThat(headers.get(WebhookSigner.HEADER)).isEqualTo(WebhookSigner.header("whsec_secret", NOW.getEpochSecond(), delivery.getPayload()));
    }

    @Test
    void failureSchedulesARetryWithBackoff() {
        nextResult = new WebhookSender.Result(500, "HTTP 500");
        dispatcher.dispatchDue();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        delivery.setAttempts(RetryPolicy.MAX_ATTEMPTS - 1);
        nextResult = new WebhookSender.Result(0, "timeout");
        dispatcher.dispatchDue();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void pausedEndpointsAreNotCalled() {
        endpoint.setActive(false);
        dispatcher.dispatchDue();
        assertThat(sentHeaders).isEmpty();
        assertThat(delivery.getLastError()).isEqualTo("Webhook is paused");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    }
}
