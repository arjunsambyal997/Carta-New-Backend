-- Scheduled maintenance announced to clients, and operator-controlled feature flags.
CREATE TABLE maintenance_window (
    id         UUID PRIMARY KEY,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    starts_at  TIMESTAMPTZ   NOT NULL,
    ends_at    TIMESTAMPTZ   NOT NULL,
    message_en VARCHAR(500)  NOT NULL,
    message_ar VARCHAR(500)  NOT NULL,
    read_only  BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_maintenance_order CHECK (ends_at > starts_at)
);
CREATE INDEX ix_maintenance_window_ends ON maintenance_window (ends_at);

CREATE TABLE feature_flag (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    flag_key   VARCHAR(64)  NOT NULL UNIQUE,
    enabled    BOOLEAN      NOT NULL DEFAULT FALSE
);
