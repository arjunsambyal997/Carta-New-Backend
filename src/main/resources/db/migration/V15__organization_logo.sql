-- A company logo shown in the app. Bytes live in their own table so loading an organization never drags them along.
ALTER TABLE organization ADD COLUMN logo_updated_at TIMESTAMPTZ;

CREATE TABLE organization_logo (
    organization_id UUID PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
    content_type    VARCHAR(32)  NOT NULL,
    size_bytes      INTEGER      NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    content         BYTEA        NOT NULL,
    uploaded_by     VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
