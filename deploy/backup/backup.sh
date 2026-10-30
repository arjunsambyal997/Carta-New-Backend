#!/bin/sh
# Dumps the database, proves the dump restores, encrypts it and copies it off the server.
#   backup.sh [label]     label is "daily" (default) or e.g. "pre-release-v1.4.0"
#
# Steps, any failure stops the run with a non-zero exit and no success ping:
#   1. pg_dump (custom format) using the PG* variables
#   2. restore-drill.sh restores it into a scratch database and checks it (RESTORE_CHECK=0 skips)
#   3. if AGE_RECIPIENT is set, encrypt with age and delete the plain dump
#   4. if RCLONE_REMOTE is set, copy to <remote>/daily/ (and <remote>/monthly/ on the 1st), prune old remote copies
#   5. delete local copies older than LOCAL_RETENTION_DAYS, ping BACKUP_PING_URL
set -eu

LABEL="${1:-daily}"
case "$LABEL" in *[!a-zA-Z0-9._-]*) echo "bad label: $LABEL" >&2; exit 2 ;; esac
BACKUP_DIR="${BACKUP_DIR:-./backups}"
LOCAL_RETENTION_DAYS="${LOCAL_RETENTION_DAYS:-7}"
REMOTE_DAILY_DAYS="${REMOTE_DAILY_DAYS:-30}"
REMOTE_MONTHLY_DAYS="${REMOTE_MONTHLY_DAYS:-365}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="sijill-$STAMP-$LABEL.dump"
FILE="$BACKUP_DIR/$NAME"

log() { echo "[backup $STAMP] $*"; }

mkdir -p "$BACKUP_DIR"
umask 077
pg_dump --format=custom --no-owner --file="$FILE.partial"
mv "$FILE.partial" "$FILE"
log "dumped $(wc -c < "$FILE") bytes"

if [ "${RESTORE_CHECK:-1}" = "1" ]; then
  /usr/local/bin/restore-drill.sh "$FILE" || { log "restore check FAILED, keeping $FILE for inspection"; exit 1; }
fi

if [ -n "${AGE_RECIPIENT:-}" ]; then
  age --encrypt --recipient "$AGE_RECIPIENT" --output "$FILE.age.partial" "$FILE"
  mv "$FILE.age.partial" "$FILE.age"
  rm -f "$FILE"
  FILE="$FILE.age"
  NAME="$NAME.age"
  log "encrypted"
else
  log "WARNING: BACKUP_AGE_RECIPIENT is not set, the dump is stored unencrypted"
fi

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copyto "$FILE" "$RCLONE_REMOTE/daily/$NAME"
  if [ "$(date -u +%d)" = "01" ] && [ "$LABEL" = "daily" ]; then
    rclone copyto "$FILE" "$RCLONE_REMOTE/monthly/$NAME"
  fi
  case "$LABEL" in pre-release-*) rclone copyto "$FILE" "$RCLONE_REMOTE/releases/$NAME" ;; esac
  rclone delete --min-age "${REMOTE_DAILY_DAYS}d" "$RCLONE_REMOTE/daily"
  rclone delete --min-age "${REMOTE_MONTHLY_DAYS}d" "$RCLONE_REMOTE/monthly"
  rclone delete --min-age "${REMOTE_MONTHLY_DAYS}d" "$RCLONE_REMOTE/releases"
  log "copied off-site to $RCLONE_REMOTE"
else
  log "WARNING: BACKUP_RCLONE_REMOTE is not set, the only copy is on this server"
fi

find "$BACKUP_DIR" -name 'sijill-*.dump*' -mtime +"$LOCAL_RETENTION_DAYS" -delete
date -u +%Y-%m-%dT%H:%M:%SZ > "$BACKUP_DIR/last-success"

if [ -n "${BACKUP_PING_URL:-}" ]; then
  wget -q -O /dev/null -T 10 "$BACKUP_PING_URL" || log "could not reach BACKUP_PING_URL"
fi
log "done: $FILE"
