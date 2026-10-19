package com.equigcc.config;

/** Counts requests per key in a fixed one-minute window; shared across instances when backed by the database. */
public interface RateLimitStore {

    /** Counts one request; returns 0 if allowed, otherwise the seconds until the window resets. */
    long hit(String key, int limit);
}
