-- EquiGCC core share register schema (PostgreSQL)

CREATE TABLE organization (
    id                    UUID PRIMARY KEY,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    legal_name_en         VARCHAR(255) NOT NULL,
    legal_name_ar         VARCHAR(255),
    jurisdiction          VARCHAR(32)  NOT NULL CHECK (jurisdiction IN ('MAINLAND_UAE','ADGM','DIFC','KSA')),
    commercial_license_no VARCHAR(64)  NOT NULL,
    base_currency         VARCHAR(3)   NOT NULL,
    certificate_prefix    VARCHAR(12)  NOT NULL,
    last_certificate_seq  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_jurisdiction_license UNIQUE (jurisdiction, commercial_license_no)
);

CREATE TABLE share_class (
    id                        UUID PRIMARY KEY,
    version                   BIGINT        NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    organization_id           UUID          NOT NULL REFERENCES organization (id),
    name                      VARCHAR(100)  NOT NULL,
    name_ar                   VARCHAR(100),
    class_type                VARCHAR(32)   NOT NULL CHECK (class_type IN ('COMMON_VOTING','NON_VOTING_ECONOMIC','PREFERRED_FOUNDER','TREASURY')),
    par_value                 NUMERIC(19,6) NOT NULL CHECK (par_value >= 0),
    currency                  VARCHAR(3)    NOT NULL,
    voting_multiplier         NUMERIC(10,4) NOT NULL DEFAULT 1 CHECK (voting_multiplier >= 0),
    dividend_preference_ratio NUMERIC(10,4) NOT NULL DEFAULT 1 CHECK (dividend_preference_ratio >= 0),
    liquidation_multiple      NUMERIC(10,4) NOT NULL DEFAULT 0 CHECK (liquidation_multiple >= 0),
    participating             BOOLEAN       NOT NULL DEFAULT FALSE,
    seniority_rank            INT           NOT NULL DEFAULT 0,
    authorized_shares         BIGINT CHECK (authorized_shares IS NULL OR authorized_shares >= 0),
    CONSTRAINT uk_share_class_org_name UNIQUE (organization_id, name)
);

CREATE TABLE shareholder (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    holder_type     VARCHAR(16)  NOT NULL CHECK (holder_type IN ('INDIVIDUAL','CORPORATE')),
    full_name_en    VARCHAR(255) NOT NULL,
    full_name_ar    VARCHAR(255),
    passport_no     VARCHAR(32),
    national_id     VARCHAR(32),
    nationality     VARCHAR(2),
    tax_id          VARCHAR(32),
    registration_no VARCHAR(64),
    address         VARCHAR(1000),
    email           VARCHAR(255)
);
CREATE INDEX idx_shareholder_org ON shareholder (organization_id);

CREATE TABLE share_issuance (
    id              UUID PRIMARY KEY,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    organization_id UUID          NOT NULL REFERENCES organization (id),
    certificate_no  VARCHAR(32)   NOT NULL UNIQUE,
    share_class_id  UUID          NOT NULL REFERENCES share_class (id),
    shareholder_id  UUID          NOT NULL REFERENCES shareholder (id),
    instrument_type VARCHAR(16)   NOT NULL CHECK (instrument_type IN ('SHARES','OPTION','WARRANT','CONVERTIBLE')),
    quantity        BIGINT        NOT NULL CHECK (quantity > 0),
    price_per_share NUMERIC(19,6) NOT NULL CHECK (price_per_share >= 0),
    currency        VARCHAR(3)    NOT NULL,
    date_issued     DATE          NOT NULL,
    status          VARCHAR(16)   NOT NULL CHECK (status IN ('ISSUED','CANCELLED','TRANSFERRED')),
    record_hash     VARCHAR(64)   NOT NULL,
    issued_by       VARCHAR(128)  NOT NULL
);
CREATE INDEX idx_issuance_org_status ON share_issuance (organization_id, status);
CREATE INDEX idx_issuance_holder ON share_issuance (shareholder_id);
