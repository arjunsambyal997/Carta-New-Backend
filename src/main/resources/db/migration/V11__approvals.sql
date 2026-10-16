-- Two-person approval: when an organization turns it on, register changes wait here until a second person approves.
ALTER TABLE organization ADD COLUMN require_approval BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE pending_change (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    kind            VARCHAR(32)  NOT NULL,
    payload         TEXT         NOT NULL,
    summary         VARCHAR(500) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(128) NOT NULL,
    decided_by      VARCHAR(128),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(500),
    result          VARCHAR(500),
    CONSTRAINT ck_pending_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX ix_pending_change_org ON pending_change (organization_id, status, created_at DESC);
