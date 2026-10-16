-- One deadline reminder per resolution.
ALTER TABLE board_resolution ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- Vesting for an option grant. The schedule follows the grant through partial exercises:
-- certificate_id always points at the grant's current certificate.
CREATE TABLE vesting_schedule (
    id                 UUID PRIMARY KEY,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    organization_id    UUID        NOT NULL REFERENCES organization (id),
    certificate_id     UUID        NOT NULL REFERENCES share_issuance (id),
    granted_quantity   BIGINT      NOT NULL CHECK (granted_quantity > 0),
    exercised_quantity BIGINT      NOT NULL DEFAULT 0 CHECK (exercised_quantity >= 0),
    start_date         DATE        NOT NULL,
    cliff_months       INT         NOT NULL CHECK (cliff_months >= 0),
    vesting_months     INT         NOT NULL CHECK (vesting_months > 0),
    created_by         VARCHAR(128) NOT NULL,
    CONSTRAINT ck_vesting_cliff CHECK (cliff_months <= vesting_months),
    CONSTRAINT ck_vesting_exercised CHECK (exercised_quantity <= granted_quantity)
);
CREATE UNIQUE INDEX uq_vesting_certificate ON vesting_schedule (certificate_id);
CREATE INDEX idx_vesting_org ON vesting_schedule (organization_id);
