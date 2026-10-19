#!/usr/bin/env bash
# LAST RESORT: replaces the live database with a backup. Everything written after that backup is lost.
#   ./restore-live.sh backups/sijill-<time>-pre-release-v1.4.0.dump.age [/path/to/age-private-key]
#
# Shows the maintenance page, stops the live backend, takes one more dump of the current state (so even this can be
# undone), restores, starts the backend again, waits for readiness and lifts the page. Run restore-drill.sh on the
# same file first. If the backup came from an older release, roll the backend back to that release afterwards.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ $# -ge 1 ] || die "usage: $0 <dump file under backups/> [age private key]"
FILE="$1"
KEY="${2:-}"
[ -r "$FILE" ] || die "cannot read $FILE"
case "$(cd "$(dirname "$FILE")" && pwd)" in
  "$DEPLOY_DIR/backups") ;;
  *) die "put the dump in $DEPLOY_DIR/backups first" ;;
esac
NAME="$(basename "$FILE")"
LIVE="$(active_color backend)"
[ -n "$LIVE" ] || die "backend has never been deployed"

if [ "${CONFIRM:-}" != yes ]; then
  read -r -p "Replace the live database with $NAME? Data written since then is lost. Type 'restore': " answer
  [ "$answer" = restore ] || die "cancelled"
fi

with_lock
"$DEPLOY_DIR/page.sh" on
app backend "$LIVE" stop >/dev/null
log "taking a safety dump of the current database"
base exec -T -e RESTORE_CHECK=0 backup backup.sh before-restore || die "safety dump failed; nothing restored. ./page.sh off to resume"

key_args=()
if [ -n "$KEY" ]; then
  base cp "$KEY" backup:/tmp/restore.key
  key_args=(-e AGE_IDENTITY_FILE=/tmp/restore.key)
fi
log "restoring $NAME"
if ! base exec -T "${key_args[@]}" -e CONFIRM=yes backup restore.sh "/backups/$NAME"; then
  base exec -T backup rm -f /tmp/restore.key || true
  die "restore failed; the live backend is stopped and the page is on. Check the dump, or restore the before-restore dump"
fi
base exec -T backup rm -f /tmp/restore.key || true

app backend "$LIVE" up -d --no-recreate >/dev/null
wait_ready backend "$LIVE" || die "backend did not come back; the page stays on. Check its logs"
"$DEPLOY_DIR/page.sh" off
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) restore-live $NAME" >> "$STATE_DIR/history.log"
log "done: database restored from $NAME"
