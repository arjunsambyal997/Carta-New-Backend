-- Server-side sign-in sessions: refresh tokens are rotated on every use and stored only as SHA-256.
CREATE TABLE auth_session (
    id                  UUID PRIMARY KEY,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    user_id             UUID         NOT NULL REFERENCES app_user (id),
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    mfa                 BOOLEAN      NOT NULL,
    refresh_hash        VARCHAR(64)     NOT NULL,
    prev_refresh_hash   VARCHAR(64),
    refresh_expires_at  TIMESTAMPTZ  NOT NULL,
    last_used_at        TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_auth_session_refresh ON auth_session (refresh_hash);
CREATE INDEX idx_auth_session_prev ON auth_session (prev_refresh_hash);
CREATE INDEX idx_auth_session_user ON auth_session (user_id);

-- Electronic signatures: a signed-in, second-factor-confirmed user attests to a fingerprint of a document.
CREATE TABLE document_signature (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    document_type   VARCHAR(16)  NOT NULL CHECK (document_type IN ('RESOLUTION','CERTIFICATE')),
    document_id     UUID         NOT NULL,
    document_ref    VARCHAR(100) NOT NULL,
    document_hash   VARCHAR(64)     NOT NULL,
    signer_email    VARCHAR(255) NOT NULL,
    signer_name     VARCHAR(200) NOT NULL,
    signer_role     VARCHAR(32)  NOT NULL,
    shareholder_id  UUID,
    statement       VARCHAR(300) NOT NULL,
    signed_at       TIMESTAMPTZ  NOT NULL,
    signature_hash  VARCHAR(64)     NOT NULL
);
CREATE UNIQUE INDEX uq_signature_signer ON document_signature (organization_id, document_type, document_id, signer_email);
CREATE INDEX idx_signature_doc ON document_signature (organization_id, document_type, document_id);
