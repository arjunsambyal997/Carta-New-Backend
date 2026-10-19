#!/usr/bin/env bash
# Blue-green deploy of one component on this server.
#   ./deploy.sh backend v1.4.0
#   ./deploy.sh frontend v1.4.0
#
# 1. Starts the proxy, database and backup job if they are not running.
# 2. Backend only: takes a pre-release backup (restore-checked, encrypted, off-site) before any migration can run.
# 3. Starts the new version in the idle colour beside the live one. Flyway migrations run as it starts, so every
#    migration must be backward compatible (expand/contract, docs/RELEASE_RUNBOOK.md): the live colour keeps serving.
# 4. Waits for readiness. If it never gets ready, the new colour is stopped and nothing has changed for clients.
# 5. Points Caddy at the new colour (graceful reload), checks the public URL, and switches straight back on failure.
# 6. Lets the old colour drain, then stops it but keeps the container, so ./rollback.sh can bring it back in seconds.
#
# Environment: SKIP_PULL=1 uses a local image; IMAGE=<ref> overrides <repo>:<version>; SKIP_BACKUP=1 (not for prod).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ $# -eq 2 ] || die "usage: $0 <backend|frontend> <version>"
COMPONENT="$1"
VERSION="$2"
port_of "$COMPONENT" >/dev/null
[[ "$VERSION" =~ ^[A-Za-z0-9._-]+$ ]] || die "bad version: $VERSION"

case "$COMPONENT" in
  backend) REPO="$(env_value BACKEND_IMAGE_REPO)"; VAR=BACKEND_IMAGE ;;
  frontend) REPO="$(env_value FRONTEND_IMAGE_REPO)"; VAR=FRONTEND_IMAGE ;;
esac
if [ -z "${IMAGE:-}" ]; then
  [ -n "$REPO" ] || die "set ${VAR}_REPO in .env"
  IMAGE="$REPO:$VERSION"
fi

with_lock
ensure_base

LIVE="$(active_color "$COMPONENT")"
TARGET="$(other "${LIVE:-green}")"
log "deploying $COMPONENT $VERSION to $TARGET (live: ${LIVE:-none} $(active_version "$COMPONENT"))"

if [ "${SKIP_PULL:-0}" != "1" ]; then
  log "pulling $IMAGE"
  docker pull -q "$IMAGE" >/dev/null || die "could not pull $IMAGE"
fi

if [ "$COMPONENT" = backend ] && [ "${SKIP_BACKUP:-0}" != "1" ] && [ -n "$LIVE" ]; then
  log "taking pre-release backup"
  base exec -T backup /usr/local/bin/backup.sh "pre-release-$VERSION" || die "pre-release backup failed; nothing was changed"
fi

cat > "$(color_env "$COMPONENT" "$TARGET")" <<EOF
COLOR=$TARGET
$VAR=$IMAGE
APP_VERSION=$VERSION
EOF

app "$COMPONENT" "$TARGET" up -d --force-recreate --remove-orphans >/dev/null

if ! wait_ready "$COMPONENT" "$TARGET"; then
  log "last log lines from $COMPONENT-$TARGET:"
  app "$COMPONENT" "$TARGET" logs --tail 60 >&2 || true
  app "$COMPONENT" "$TARGET" stop >/dev/null || true
  die "$COMPONENT $VERSION never became ready; clients stayed on ${LIVE:-nothing}"
fi

write_upstream "$COMPONENT" "$TARGET"
reload_proxy
log "traffic switched to $COMPONENT-$TARGET"

smoke_ok=1
case "$COMPONENT" in
  backend)
    status="$(public_get /api/v1/system/status)" || smoke_ok=0
    [[ "${status:-}" == *"\"version\":\"$VERSION\""* ]] || smoke_ok=0
    ;;
  frontend)
    public_get /login >/dev/null || smoke_ok=0
    ;;
esac

if [ "$smoke_ok" != 1 ]; then
  if [ -n "$LIVE" ]; then
    write_upstream "$COMPONENT" "$LIVE"
    reload_proxy
    log "public check failed: traffic switched back to $COMPONENT-$LIVE"
  fi
  app "$COMPONENT" "$TARGET" stop >/dev/null || true
  die "$COMPONENT $VERSION failed the public check"
fi

if [ -n "$LIVE" ]; then
  cp "$STATE_DIR/$COMPONENT.version" "$STATE_DIR/$COMPONENT.previous-version" 2>/dev/null || true
fi
echo "$TARGET" > "$STATE_DIR/$COMPONENT.color"
echo "$VERSION" > "$STATE_DIR/$COMPONENT.version"
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) deploy $COMPONENT $VERSION $TARGET (was ${LIVE:-none})" >> "$STATE_DIR/history.log"

if [ -n "$LIVE" ]; then
  log "draining $COMPONENT-$LIVE for ${DRAIN_SECONDS}s"
  sleep "$DRAIN_SECONDS"
  app "$COMPONENT" "$LIVE" stop >/dev/null
  log "stopped $COMPONENT-$LIVE (kept for ./rollback.sh $COMPONENT)"
fi

docker image prune -f >/dev/null 2>&1 || true
log "done: $COMPONENT $VERSION is live on $TARGET"
