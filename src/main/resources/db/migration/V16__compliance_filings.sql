-- Records that a regulatory filing was made, which clears the matching deadline in the compliance view.
CREATE TABLE compliance_filing (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    rule_key        VARCHAR(64)  NOT NULL,
    filed_on        DATE         NOT NULL,
    reference       VARCHAR(200),
    filed_by        VARCHAR(255) NOT NULL
);
CREATE INDEX ix_compliance_filing_org_rule ON compliance_filing (organization_id, rule_key, created_at DESC);
