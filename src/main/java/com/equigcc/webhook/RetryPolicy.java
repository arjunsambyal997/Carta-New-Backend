package com.equigcc.webhook;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Wait times between attempts: 1 minute, 5 minutes, 30 minutes, 2 hours, 12 hours; then the delivery is marked failed. */
public final class RetryPolicy {

    static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(12));

    public static final int MAX_ATTEMPTS = BACKOFF.size() + 1;

    private RetryPolicy() {
    }

    /** The wait before the next attempt, given how many attempts have now been made; empty when there are none left. */
    public static Optional<Duration> nextDelay(int attemptsMade) {
        return attemptsMade >= 1 && attemptsMade <= BACKOFF.size() ? Optional.of(BACKOFF.get(attemptsMade - 1)) : Optional.empty();
    }
}
