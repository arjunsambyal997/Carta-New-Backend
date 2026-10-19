package com.equigcc.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    @Test
    void headerCarriesTimestampAndHmacOfTimestampDotBody() {
        String header = WebhookSigner.header("whsec_test", 1760864400L, "{\"a\":1}");
        assertThat(header).startsWith("t=1760864400,v1=");
        assertThat(header.substring(header.indexOf("v1=") + 3)).isEqualTo(WebhookSigner.hmac("whsec_test", "1760864400.{\"a\":1}")).hasSize(64);
    }

    @Test
    void signatureChangesWithSecretBodyAndTime() {
        String base = WebhookSigner.header("s1", 1L, "x");
        assertThat(WebhookSigner.header("s2", 1L, "x")).isNotEqualTo(base);
        assertThat(WebhookSigner.header("s1", 1L, "y")).isNotEqualTo(base);
        assertThat(WebhookSigner.header("s1", 2L, "x")).isNotEqualTo(base);
    }

    @Test
    void matchesKnownHmacSha256Vector() {
        // RFC 4231 style check: HMAC-SHA256(key="key", "The quick brown fox jumps over the lazy dog")
        assertThat(WebhookSigner.hmac("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }
}
