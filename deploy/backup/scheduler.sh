#!/bin/sh
# Runs backup.sh once a day at BACKUP_HOUR_UTC (default 22:00 UTC = 02:00 Gulf time), forever.
# A failed run is logged and retried the next day; BACKUP_PING_URL (if set) alerts on the missing success ping.
set -u
HOUR="${BACKUP_HOUR_UTC:-22}"

while true; do
  now=$(date -u +%s)
  today=$(date -u +%Y-%m-%d)
  next=$(date -u -d "$today $(printf '%02d' "$HOUR"):00:00" +%s)
  [ "$next" -le "$now" ] && next=$((next + 86400))
  echo "next backup at $(date -u -d "@$next" '+%Y-%m-%dT%H:%M:%SZ')"
  sleep $((next - now))
  /usr/local/bin/backup.sh daily || echo "backup failed at $(date -u '+%Y-%m-%dT%H:%M:%SZ')" >&2
done
