#!/bin/sh
# Restores a dump OVER a database. Run restore-drill.sh on the same file first.
#   PGDATABASE=equigcc CONFIRM=yes restore.sh <file.dump | file.dump.age>
# Restoring over the live database loses everything written since the dump: stop both backend colours first
# (deploy/page.sh on), restore, then start again (deploy/rollback.sh or deploy.sh). See docs/RELEASE_RUNBOOK.md.
set -eu
[ $# -eq 1 ] || { echo "usage: $0 <dump file>" >&2; exit 2; }
SRC="$1"
[ -r "$SRC" ] || { echo "cannot read $SRC" >&2; exit 2; }
: "${PGDATABASE:?set PGDATABASE}"
[ "${CONFIRM:-}" = "yes" ] || { echo "this replaces database $PGDATABASE; rerun with CONFIRM=yes" >&2; exit 2; }

TMP=""
trap '[ -n "$TMP" ] && rm -f "$TMP"' EXIT
DUMP="$SRC"
case "$SRC" in
  *.age)
    [ -r "${AGE_IDENTITY_FILE:-}" ] || { echo "encrypted dump: set AGE_IDENTITY_FILE to the private key" >&2; exit 2; }
    TMP="$(mktemp)"
    age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$TMP" "$SRC"
    DUMP="$TMP"
    ;;
esac

pg_restore --clean --if-exists --no-owner --exit-on-error --single-transaction --dbname="$PGDATABASE" "$DUMP"
echo "restored $SRC into $PGDATABASE"
