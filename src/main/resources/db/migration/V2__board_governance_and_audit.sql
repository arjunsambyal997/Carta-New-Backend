-- Board resolutions, votes and the append-only audit trail

CREATE TABLE board_resolution (
    id                 UUID PRIMARY KEY,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    organization_id    UUID          NOT NULL REFERENCES organization (id),
    title              VARCHAR(255)  NOT NULL,
    title_ar           VARCHAR(255),
    description        VARCHAR(4000),
    threshold          VARCHAR(16)   NOT NULL CHECK (threshold IN ('ORDINARY','SPECIAL')),
    quorum_pct         NUMERIC(5,2)  NOT NULL CHECK (quorum_pct >= 0 AND quorum_pct <= 100),
    voting_deadline    TIMESTAMPTZ   NOT NULL,
    status             VARCHAR(16)   NOT NULL CHECK (status IN ('DRAFT','ACTIVE','PASSED','REJECTED')),
    total_voting_power NUMERIC(24,4),
    created_by         VARCHAR(128)  NOT NULL,
    opened_at          TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ
);
CREATE INDEX idx_resolution_org ON board_resolution (organization_id, created_at DESC);

CREATE TABLE vote_record (
    id             UUID PRIMARY KEY,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    resolution_id  UUID          NOT NULL REFERENCES board_resolution (id),
    shareholder_id UUID          NOT NULL REFERENCES shareholder (id),
    vote           VARCHAR(8)    NOT NULL CHECK (vote IN ('FOR','AGAINST','ABSTAIN')),
    weight_applied NUMERIC(24,4) NOT NULL CHECK (weight_applied > 0),
    cast_at        TIMESTAMPTZ   NOT NULL,
    cast_by        VARCHAR(128)  NOT NULL,
    CONSTRAINT uk_vote_resolution_shareholder UNIQUE (resolution_id, shareholder_id)
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    organization_id UUID         NOT NULL,
    entity_name     VARCHAR(64)  NOT NULL,
    entity_id       UUID         NOT NULL,
    action          VARCHAR(16)  NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE')),
    detail          VARCHAR(1000),
    performed_by    VARCHAR(128) NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    prev_hash       VARCHAR(64)  NOT NULL,
    hash            VARCHAR(64)  NOT NULL
);
CREATE INDEX idx_audit_org ON audit_log (organization_id, id);

-- Append-only: even a compromised application role cannot rewrite history without dropping the trigger.
CREATE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
