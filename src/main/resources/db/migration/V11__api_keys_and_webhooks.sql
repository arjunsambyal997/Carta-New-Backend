-- Read-only API keys for integrations. Only the SHA-256 of a key is stored; the key itself is shown once.
CREATE TABLE api_key (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(120) NOT NULL,
    key_prefix      VARCHAR(16)  NOT NULL,
    key_hash        VARCHAR(64)  NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_api_key_hash ON api_key (key_hash);
CREATE INDEX idx_api_key_org ON api_key (organization_id);

-- Outbound webhooks: signed JSON posts to an https endpoint when something happens in the register.
CREATE TABLE webhook_endpoint (
    id              UUID PRIMARY KEY,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    organization_id UUID          NOT NULL REFERENCES organization (id),
    url             VARCHAR(2000) NOT NULL,
    secret_enc      VARCHAR(400)  NOT NULL,
    events          VARCHAR(500)  NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(255)  NOT NULL
);
CREATE INDEX idx_webhook_endpoint_org ON webhook_endpoint (organization_id);

CREATE TABLE webhook_delivery (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    endpoint_id     UUID         NOT NULL REFERENCES webhook_endpoint (id) ON DELETE CASCADE,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    event_type      VARCHAR(60)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(12)  NOT NULL CHECK (status IN ('PENDING','DELIVERED','FAILED')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    response_code   INT,
    last_error      VARCHAR(500),
    delivered_at    TIMESTAMPTZ
);
CREATE INDEX idx_webhook_delivery_due ON webhook_delivery (status, next_attempt_at);
CREATE INDEX idx_webhook_delivery_endpoint ON webhook_delivery (endpoint_id, created_at DESC);
