-- Certificate numbers are looked up globally by the public verification URL, so two organizations must not share a prefix.
CREATE UNIQUE INDEX uk_org_certificate_prefix ON organization (upper(certificate_prefix));
