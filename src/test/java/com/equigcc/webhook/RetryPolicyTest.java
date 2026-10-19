package com.equigcc.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void backsOffThenGivesUp() {
        assertThat(RetryPolicy.nextDelay(1)).contains(Duration.ofMinutes(1));
        assertThat(RetryPolicy.nextDelay(2)).contains(Duration.ofMinutes(5));
        assertThat(RetryPolicy.nextDelay(5)).contains(Duration.ofHours(12));
        assertThat(RetryPolicy.nextDelay(6)).isEmpty();
        assertThat(RetryPolicy.nextDelay(0)).isEmpty();
        assertThat(RetryPolicy.MAX_ATTEMPTS).isEqualTo(6);
    }
}
