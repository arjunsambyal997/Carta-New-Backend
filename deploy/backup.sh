#!/bin/sh
# Dumps the database to $BACKUP_DIR, checks the dump can be read back, and deletes dumps older than $RETENTION_DAYS.
# Uses the standard PG* environment variables (PGHOST, PGUSER, PGPASSWORD, PGDATABASE). Works against any Postgres.
set -eu

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$BACKUP_DIR/equigcc-$STAMP.dump"

mkdir -p "$BACKUP_DIR"
pg_dump --format=custom --no-owner --file="$FILE.partial"
# A dump that cannot be listed is useless: fail loudly instead of keeping it.
if ! pg_restore --list "$FILE.partial" > /dev/null; then
  rm -f "$FILE.partial"
  echo "backup verification failed" >&2
  exit 1
fi
mv "$FILE.partial" "$FILE"
chmod 600 "$FILE"
find "$BACKUP_DIR" -name 'equigcc-*.dump' -mtime +"$RETENTION_DAYS" -delete
echo "backup written: $FILE ($(wc -c < "$FILE") bytes)"
