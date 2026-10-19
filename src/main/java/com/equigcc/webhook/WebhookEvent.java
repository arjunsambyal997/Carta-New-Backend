package com.equigcc.webhook;

import java.util.Arrays;
import java.util.Optional;

/** Things a subscriber can be told about. The wire name is what appears in {@code type} and in subscriptions. */
public enum WebhookEvent {
    CERTIFICATE_ISSUED("certificate.issued"),
    TRANSFER_RECORDED("transfer.recorded"),
    BUYBACK_RECORDED("buyback.recorded"),
    RESOLUTION_OPENED("resolution.opened"),
    RESOLUTION_CLOSED("resolution.closed"),
    PING("ping");

    private final String wireName;

    WebhookEvent(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<WebhookEvent> parse(String wireName) {
        return Arrays.stream(values()).filter(e -> e.wireName.equals(wireName)).findFirst();
    }
}
