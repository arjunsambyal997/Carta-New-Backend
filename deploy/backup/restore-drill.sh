#!/bin/sh
# Proves a dump can be restored: restores it into a throwaway database, runs sanity checks, drops the database.
# Never touches the live database.
#   restore-drill.sh <file.dump | file.dump.age>
# Encrypted dumps need AGE_IDENTITY_FILE (the private key; keep it off the server, copy it in only for the drill).
# Uses PGHOST/PGUSER/PGPASSWORD; DRILL_DB names the scratch database (default sijill_restore_drill).
set -eu
[ $# -eq 1 ] || { echo "usage: $0 <dump file>" >&2; exit 2; }
SRC="$1"
[ -r "$SRC" ] || { echo "cannot read $SRC" >&2; exit 2; }
DRILL_DB="${DRILL_DB:-sijill_restore_drill}"
LIVE_DB="${PGDATABASE:-equigcc}"
[ "$DRILL_DB" != "$LIVE_DB" ] || { echo "DRILL_DB must not be the live database" >&2; exit 2; }

TMP=""
cleanup() {
  [ -n "$TMP" ] && rm -f "$TMP"
  dropdb --if-exists --maintenance-db="$LIVE_DB" "$DRILL_DB" 2>/dev/null || true
}
trap cleanup EXIT

DUMP="$SRC"
case "$SRC" in
  *.age)
    [ -r "${AGE_IDENTITY_FILE:-}" ] || { echo "encrypted dump: set AGE_IDENTITY_FILE to the private key" >&2; exit 2; }
    TMP="$(mktemp)"
    age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$TMP" "$SRC"
    DUMP="$TMP"
    ;;
esac

pg_restore --list "$DUMP" > /dev/null
dropdb --if-exists --maintenance-db="$LIVE_DB" "$DRILL_DB"
createdb --maintenance-db="$LIVE_DB" "$DRILL_DB"
pg_restore --no-owner --exit-on-error --single-transaction --dbname="$DRILL_DB" "$DUMP"

q() { psql -X -q -t -A -v ON_ERROR_STOP=1 --dbname="$DRILL_DB" -c "$1"; }

failed=$(q "SELECT count(*) FROM flyway_schema_history WHERE NOT success")
[ "$failed" = "0" ] || { echo "restore drill: $failed failed migrations recorded" >&2; exit 1; }
version=$(q "SELECT max(version::int) FROM flyway_schema_history WHERE version ~ '^[0-9]+\$'")
for t in organization share_class shareholder share_issuance audit_log app_user; do
  q "SELECT 1 FROM $t LIMIT 1" > /dev/null
done
orgs=$(q "SELECT count(*) FROM organization")
issuances=$(q "SELECT count(*) FROM share_issuance")
audit=$(q "SELECT count(*) FROM audit_log")
echo "restore drill OK: schema V$version, $orgs organisations, $issuances issuances, $audit audit entries"
