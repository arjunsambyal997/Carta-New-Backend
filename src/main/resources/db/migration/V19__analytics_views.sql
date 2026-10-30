-- Read-only views for the analytics dashboard (Metabase). Every view carries organization_id, and the dashboard is
-- embedded with that id locked by a signed token, so a company only ever sees its own rows.
-- No personal identifiers (passport, national ID, tax ID, address, date of birth), credentials or document contents.
-- The reporting role gets SELECT on this schema only (deploy/analytics/init-db.sql); views run with their owner's
-- rights, so it needs no access to the tables underneath.
-- A later migration that changes a column used here must drop and recreate the view that uses it.
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE VIEW analytics.issuances AS
SELECT i.organization_id,
       i.id                                AS issuance_id,
       i.certificate_no,
       i.date_issued,
       i.status,
       i.instrument_type,
       i.quantity,
       i.price_per_share,
       i.quantity * i.price_per_share      AS consideration,
       i.currency,
       c.name                              AS share_class,
       c.class_type,
       c.voting_multiplier,
       h.id                                AS shareholder_id,
       h.full_name_en                      AS shareholder,
       h.holder_type
FROM share_issuance i
         JOIN share_class c ON c.id = i.share_class_id
         JOIN shareholder h ON h.id = i.shareholder_id;

-- Shares currently held: issued certificates only (cancelled, transferred and converted ones are history).
CREATE VIEW analytics.holdings AS
SELECT organization_id,
       shareholder_id,
       shareholder,
       holder_type,
       share_class,
       class_type,
       sum(quantity)                       AS shares,
       sum(quantity * voting_multiplier)   AS votes
FROM analytics.issuances
WHERE status = 'ISSUED'
GROUP BY organization_id, shareholder_id, shareholder, holder_type, share_class, class_type;

CREATE VIEW analytics.resolutions AS
SELECT r.organization_id,
       r.id               AS resolution_id,
       r.title,
       r.threshold,
       r.status,
       r.created_at,
       r.voting_deadline,
       r.total_voting_power,
       (SELECT count(*) FROM vote_record v WHERE v.resolution_id = r.id)                        AS votes_cast,
       (SELECT coalesce(sum(v.weight_applied), 0) FROM vote_record v WHERE v.resolution_id = r.id) AS weight_voted
FROM board_resolution r;

-- Register activity per day, from the audit trail (who did it is left out).
CREATE VIEW analytics.activity AS
SELECT organization_id,
       (occurred_at AT TIME ZONE 'UTC')::date AS day,
       entity_name,
       action,
       count(*)                               AS events
FROM audit_log
GROUP BY organization_id, (occurred_at AT TIME ZONE 'UTC')::date, entity_name, action;
