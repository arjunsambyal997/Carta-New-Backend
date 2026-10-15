-- Single-use password reset tokens; only a SHA-256 of the token is stored.
CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    user_id     UUID         NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ
);
CREATE INDEX idx_reset_user ON password_reset_token (user_id, created_at DESC);
