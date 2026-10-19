package com.equigcc.webhook;

import java.net.URI;
import java.util.Map;

/** Sends one HTTP POST. A seam so the dispatcher can be tested without a network. */
public interface WebhookSender {

    /** {@code statusCode} is 0 when no response was received; {@code error} then says why. */
    record Result(int statusCode, String error) {
        public boolean success() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    Result post(URI uri, Map<String, String> headers, String body);
}
