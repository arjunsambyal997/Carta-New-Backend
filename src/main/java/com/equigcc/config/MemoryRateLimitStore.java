package com.equigcc.config;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process counts: fast, but each backend instance counts on its own. */
public class MemoryRateLimitStore implements RateLimitStore {

    static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public MemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long hit(String key, int limit) {
        long now = clock.millis();
        if (windows.size() > 20_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
        Window w = windows.merge(key, new Window(now, 1), (old, fresh) -> now - old.start() >= WINDOW_MS ? fresh : new Window(old.start(), old.count() + 1));
        if (w.count() > limit) {
            return Math.max(1, (WINDOW_MS - (now - w.start()) + 999) / 1000);
        }
        return 0;
    }
}
