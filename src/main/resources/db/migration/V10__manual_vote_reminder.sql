-- Officers can nudge non-voters by hand; this throttles that to once an hour.
ALTER TABLE board_resolution ADD COLUMN manual_reminder_at TIMESTAMPTZ;
