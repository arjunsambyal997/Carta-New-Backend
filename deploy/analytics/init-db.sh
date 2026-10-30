#!/usr/bin/env bash
# One-off (safe to re-run): makes the two database roles the analytics service needs, inside the stack's Postgres.
#   metabase          owns the "metabase" database, where Metabase keeps its own settings and dashboards
#   sijill_analytics  read-only: SELECT on the analytics.* views of the register (V19), nothing else
# Run on the server from /opt/sijill:   deploy/analytics/init-db.sh
# Reads METABASE_DB_PASSWORD and ANALYTICS_DB_PASSWORD from .env.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
: "${METABASE_DB_PASSWORD:?set METABASE_DB_PASSWORD in .env}"
: "${ANALYTICS_DB_PASSWORD:?set ANALYTICS_DB_PASSWORD in .env}"

docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec -T db \
  psql -v ON_ERROR_STOP=1 -U equigcc -d equigcc \
  -v mb_pw="$METABASE_DB_PASSWORD" -v ro_pw="$ANALYTICS_DB_PASSWORD" < analytics/init-db.sql
echo "Analytics roles ready."
