CREATE TABLE rate_limit_hits (
    hit_key      VARCHAR(200) PRIMARY KEY,
    window_start BIGINT       NOT NULL,
    hits         INTEGER      NOT NULL
);
