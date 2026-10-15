-- Who owns a corporate shareholder. Used to look through holding companies to the natural persons behind them.
CREATE TABLE ownership_link (
    id                UUID PRIMARY KEY,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    owner_id          UUID         NOT NULL REFERENCES shareholder (id),
    owned_id          UUID         NOT NULL REFERENCES shareholder (id),
    economic_pct      NUMERIC(7,4) NOT NULL CHECK (economic_pct > 0 AND economic_pct <= 100),
    voting_pct        NUMERIC(7,4) NOT NULL CHECK (voting_pct >= 0 AND voting_pct <= 100),
    CONSTRAINT uk_ownership_owner_owned UNIQUE (owner_id, owned_id),
    CONSTRAINT ck_ownership_not_self CHECK (owner_id <> owned_id)
);
CREATE INDEX idx_ownership_org ON ownership_link (organization_id);
CREATE INDEX idx_ownership_owned ON ownership_link (owned_id);
