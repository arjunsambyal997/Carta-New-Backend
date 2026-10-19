#!/usr/bin/env bash
# Puts the previous version of one component back in front of clients, in seconds, with no rebuild or pull.
#   ./rollback.sh backend
#   ./rollback.sh frontend
# Works because deploy.sh keeps the previous colour's container (stopped) and every migration is backward compatible.
# To go further back than one release, deploy the older tag instead: ./deploy.sh backend v1.2.0
# This never touches data. If a release damaged data, see "Restore" in docs/RELEASE_RUNBOOK.md.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ $# -eq 1 ] || die "usage: $0 <backend|frontend>"
COMPONENT="$1"
port_of "$COMPONENT" >/dev/null

with_lock
LIVE="$(active_color "$COMPONENT")"
[ -n "$LIVE" ] || die "$COMPONENT has never been deployed"
PREV="$(other "$LIVE")"
[ -r "$(color_env "$COMPONENT" "$PREV")" ] || die "no previous $COMPONENT to roll back to"
PREV_VERSION="$(sed -n 's/^APP_VERSION=//p' "$(color_env "$COMPONENT" "$PREV")")"

log "rolling $COMPONENT back from $(active_version "$COMPONENT") ($LIVE) to $PREV_VERSION ($PREV)"
app "$COMPONENT" "$PREV" up -d --no-recreate >/dev/null
wait_ready "$COMPONENT" "$PREV" || die "$COMPONENT-$PREV did not become ready; $LIVE is still serving"

write_upstream "$COMPONENT" "$PREV"
reload_proxy
log "traffic switched to $COMPONENT-$PREV"

echo "$PREV" > "$STATE_DIR/$COMPONENT.color"
cp "$STATE_DIR/$COMPONENT.version" "$STATE_DIR/$COMPONENT.previous-version" 2>/dev/null || true
echo "$PREV_VERSION" > "$STATE_DIR/$COMPONENT.version"
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) rollback $COMPONENT $PREV_VERSION $PREV (was $LIVE)" >> "$STATE_DIR/history.log"

sleep "$DRAIN_SECONDS"
app "$COMPONENT" "$LIVE" stop >/dev/null
log "done: $COMPONENT $PREV_VERSION is live on $PREV; $LIVE is stopped and kept"
