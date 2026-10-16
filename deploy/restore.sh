#!/bin/sh
# Restores a dump made by backup.sh into the database named by the PG* variables. It REPLACES what is there.
#   PGHOST=... PGUSER=... PGPASSWORD=... PGDATABASE=equigcc_restore ./restore.sh backups/equigcc-20261015T020000Z.dump
# Restore into a spare database first and look at it before touching production.
set -eu
[ $# -eq 1 ] || { echo "usage: $0 <dump file>" >&2; exit 2; }
[ -r "$1" ] || { echo "cannot read $1" >&2; exit 2; }
pg_restore --clean --if-exists --no-owner --single-transaction --dbname="${PGDATABASE:?set PGDATABASE}" "$1"
echo "restored $1 into $PGDATABASE"
