package com.equigcc.auth;

import com.equigcc.config.StartupSecretsCheck;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 Appendix B, SHA-1 secret "12345678901234567890", last six digits of the 8-digit values. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Vectors() {
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void base32RoundTrips() {
        String encoded = Totp.base32(RFC_KEY);
        assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.decodeBase32(encoded)).isEqualTo(RFC_KEY);
    }

    @Test
    void acceptsAdjacentStepButNotFurther() {
        String secret = Totp.base32(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1111111109L);
        long step = Totp.stepOf(now);
        String prev = Totp.code(RFC_KEY, step - 1);
        String far = Totp.code(RFC_KEY, step - 3);
        assertThat(Totp.matchStep(secret, prev, now, 1)).isEqualTo(step - 1);
        assertThat(Totp.matchStep(secret, far, now, 1)).isEqualTo(-1);
        assertThat(Totp.matchStep(secret, "abc123", now, 1)).isEqualTo(-1);
    }

    @Test
    void newSecretsAreRandomAndValid() {
        String a = Totp.newSecret();
        assertThat(a).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.newSecret()).isNotEqualTo(a);
    }

    @Test
    void secretCipherRoundTripsAndIsRandomised() {
        var cipher = new SecretCipher(StartupSecretsCheck.DEV_ENCRYPTION_KEY);
        String one = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(one)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.encrypt("JBSWY3DPEHPK3PXP")).isNotEqualTo(one);
    }
}
