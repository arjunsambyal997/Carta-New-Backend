package com.equigcc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts in the database so every backend instance sees the same numbers. One atomic upsert per call; a failing database lets
 * the request through (the account lockout still applies) rather than locking everyone out.
 */
public class JdbcRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcRateLimitStore.class);
    private static final long WINDOW_MS = MemoryRateLimitStore.WINDOW_MS;
    private static final int CLEAN_EVERY = 500;

    private static final String UPSERT = """
            INSERT INTO rate_limit_hits (hit_key, window_start, hits) VALUES (?, ?, 1)
            ON CONFLICT (hit_key) DO UPDATE SET
              hits = CASE WHEN rate_limit_hits.window_start <= ? THEN 1 ELSE rate_limit_hits.hits + 1 END,
              window_start = CASE WHEN rate_limit_hits.window_start <= ? THEN EXCLUDED.window_start ELSE rate_limit_hits.window_start END
            RETURNING window_start, hits
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AtomicInteger calls = new AtomicInteger();

    public JdbcRateLimitStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public long hit(String key, int limit) {
        long now = clock.millis();
        try {
            long expiredBefore = now - WINDOW_MS;
            long[] row = jdbc.queryForObject(UPSERT, (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, key, now, expiredBefore, expiredBefore);
            if (calls.incrementAndGet() % CLEAN_EVERY == 0) {
                jdbc.update("DELETE FROM rate_limit_hits WHERE window_start < ?", now - 2 * WINDOW_MS);
            }
            if (row != null && row[1] > limit) {
                return Math.max(1, (WINDOW_MS - (now - row[0]) + 999) / 1000);
            }
        } catch (RuntimeException e) {
            log.warn("Shared rate limit unavailable, letting the request through: {}", e.toString());
        }
        return 0;
    }
}
