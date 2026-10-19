#!/usr/bin/env bash
# One-screen health summary: what is live, what is kept for rollback, the last backup, and the public checks.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

for c in backend frontend; do
  live="$(active_color "$c")"
  if [ -z "$live" ]; then echo "$c: not deployed"; continue; fi
  prev="$(other "$live")"
  prev_version="$(sed -n 's/^APP_VERSION=//p' "$(color_env "$c" "$prev")" 2>/dev/null || true)"
  echo "$c: $(active_version "$c") live on $live; rollback target: ${prev_version:-none}"
done
echo "last good backup: $(cat "$DEPLOY_DIR/backups/last-success" 2>/dev/null || echo never)"
echo -n "public API status: "; public_get /api/v1/system/status || echo "FAILING"
echo
echo -n "public readiness:  "; public_get /actuator/health/readiness || echo "FAILING"
echo
echo "recent deploys:"; tail -n 5 "$STATE_DIR/history.log" 2>/dev/null || echo "  none"
base ps --format 'table {{.Service}}\t{{.Status}}'
