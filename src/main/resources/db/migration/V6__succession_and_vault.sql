-- Converted certificates keep a link to the certificate that replaced them.
ALTER TABLE share_issuance DROP CONSTRAINT share_issuance_status_check;
ALTER TABLE share_issuance ADD CONSTRAINT share_issuance_status_check
    CHECK (status IN ('ISSUED','CANCELLED','TRANSFERRED','CONVERTED'));
ALTER TABLE share_issuance ADD COLUMN predecessor_id UUID REFERENCES share_issuance (id);
ALTER TABLE share_issuance ADD COLUMN succession_rule_id UUID;
CREATE INDEX idx_issuance_predecessor ON share_issuance (predecessor_id);

CREATE TABLE succession_rule (
    id                UUID PRIMARY KEY,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    name              VARCHAR(200) NOT NULL,
    trigger_event     VARCHAR(32)  NOT NULL CHECK (trigger_event IN
                        ('FOUNDER_DEATH','FOUNDER_INCAPACITY','FOUNDER_EXIT','TRANSFER_TO_HEIR','IPO','CUSTOM')),
    subject_id        UUID REFERENCES shareholder (id),
    from_class_id     UUID         NOT NULL REFERENCES share_class (id),
    to_class_id       UUID         NOT NULL REFERENCES share_class (id),
    ratio_numerator   BIGINT       NOT NULL CHECK (ratio_numerator > 0),
    ratio_denominator BIGINT       NOT NULL CHECK (ratio_denominator > 0),
    beneficiary_id    UUID REFERENCES shareholder (id),
    legal_reference   VARCHAR(500) NOT NULL,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('DRAFT','EXECUTED','CANCELLED')),
    resolution_id     UUID REFERENCES board_resolution (id),
    executed_at       TIMESTAMPTZ,
    executed_by       VARCHAR(128),
    created_by        VARCHAR(128) NOT NULL,
    CONSTRAINT ck_succession_classes_differ CHECK (from_class_id <> to_class_id)
);
CREATE INDEX idx_succession_org ON succession_rule (organization_id, status);

CREATE TABLE vault_document (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    title           VARCHAR(200) NOT NULL,
    category        VARCHAR(32)  NOT NULL CHECK (category IN
                      ('ARTICLES_OF_ASSOCIATION','MEMORANDUM','NOTARY_RECEIPT','SHAREHOLDER_AGREEMENT','RESOLUTION_MINUTES','LICENCE','OTHER')),
    file_name       VARCHAR(200) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256          VARCHAR(64)  NOT NULL,
    content         BYTEA        NOT NULL,
    uploaded_by     VARCHAR(128) NOT NULL
);
CREATE INDEX idx_vault_org ON vault_document (organization_id, created_at DESC);
