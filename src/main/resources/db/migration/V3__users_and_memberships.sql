-- Accounts, per-organization roles and TOTP second factor

CREATE TABLE app_user (
    id               UUID PRIMARY KEY,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    email            VARCHAR(255) NOT NULL,
    display_name     VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    totp_secret_enc  VARCHAR(255),
    totp_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_last_step   BIGINT       NOT NULL DEFAULT 0,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_app_user_email ON app_user (lower(email));

CREATE TABLE membership (
    id              UUID PRIMARY KEY,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    user_id         UUID        NOT NULL REFERENCES app_user (id),
    organization_id UUID        NOT NULL REFERENCES organization (id),
    role            VARCHAR(32) NOT NULL CHECK (role IN ('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','SHAREHOLDER','LEGAL_AUDITOR')),
    shareholder_id  UUID REFERENCES shareholder (id),
    CONSTRAINT uk_membership_user_org UNIQUE (user_id, organization_id)
);
CREATE INDEX idx_membership_org ON membership (organization_id);
