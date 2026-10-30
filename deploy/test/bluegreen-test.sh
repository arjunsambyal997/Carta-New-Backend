#!/usr/bin/env bash
# End-to-end test of the production scripts on a local Docker daemon, with DOMAIN=localhost (Caddy's internal TLS).
# Uses a throwaway copy of deploy/ so no state is left in the repo. Needs ports 80 and 443 free.
#   BACKEND_TEST_IMAGE=<image built from this repo> deploy/test/bluegreen-test.sh
# Checks: first deploy, zero failed requests while switching colours, rollback, a release that never gets ready
# (live colour untouched), the full maintenance page, the maintenance banner API, feature flags, and a backup that
# restores into a scratch database.
set -euo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${BACKEND_TEST_IMAGE:?build the backend image and set BACKEND_TEST_IMAGE}"
WORK="$(mktemp -d)"
T="$(mktemp -d)"   # test scratch, kept apart from the deploy copy
LOG="$T/deploy.log"
cp -r "$SRC/." "$WORK/"
rm -rf "$WORK/state" "$WORK/backups" "$WORK/.env"
cd "$WORK"

pass() { printf '  ok  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*" >&2; exit 1; }
cleanup() {
  for p in sijill-backend-blue sijill-backend-green sijill-frontend-blue sijill-frontend-green sijill; do
    docker compose -p "$p" down -v --remove-orphans >/dev/null 2>&1 || true
  done
  docker network rm sijill >/dev/null 2>&1 || true
  rm -rf "$WORK" "$T"
}
trap '[ $? -ne 0 ] && tail -n 40 "$LOG" >&2; cleanup' EXIT

cat > .env <<EOF
DOMAIN=localhost
BACKEND_IMAGE_REPO=unused
FRONTEND_IMAGE_REPO=unused
POSTGRES_PASSWORD=test-$(openssl rand -hex 8)
JWT_SECRET=$(openssl rand -base64 48)
ENCRYPTION_KEY=$(openssl rand -base64 32)
SYSTEM_ADMIN_TOKEN=$(openssl rand -hex 16)
MAIL_FROM=no-reply@localhost
SPRING_MAIL_HOST=localhost
BACKEND_MEM_LIMIT=1g
BACKUP_RCLONE_REMOTE=
BACKUP_AGE_RECIPIENT=
EOF

# A stand-in frontend: answers 200 on every path and says which version it is.
for v in 1 2; do
  printf 'FROM caddy:2-alpine\nCMD ["caddy","respond","--listen",":3000","frontend %s"]\n' "f$v" \
    | docker build -q -t "sijill-frontend-stub:f$v" - >/dev/null
done
export SKIP_PULL=1 DRAIN_SECONDS=3

echo "first deploy"
IMAGE="$IMAGE" ./deploy.sh backend v1 >>"$LOG" 2>&1
IMAGE=sijill-frontend-stub:f1 ./deploy.sh frontend f1 >>"$LOG" 2>&1
curl -fsk https://localhost/api/v1/system/status | grep -q '"version":"v1"' && pass "backend v1 live" || fail "backend v1"
curl -fsk https://localhost/login | grep -q 'frontend f1' && pass "frontend f1 live" || fail "frontend f1"
curl -fsk https://localhost/actuator/health/readiness | grep -q UP && pass "readiness exposed" || fail "readiness"

echo "zero-downtime switch"
errors="$T/failed-requests"; : > "$errors"
( end=$(( $(date +%s) + 120 ))
  while [ "$(date +%s)" -lt "$end" ] && [ ! -f "$T/stop" ]; do
    # ~6 requests a second: steady traffic, and under the API rate limit (600 a minute per client).
    code=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 https://localhost/api/v1/system/status || true)
    [ "$code" = 200 ] || echo "$code" >> "$errors"
    sleep 0.15
  done ) &
loader=$!
IMAGE="$IMAGE" ./deploy.sh backend v2 >>"$LOG" 2>&1
touch "$T/stop"; wait "$loader"
[ ! -s "$errors" ] && pass "no failed requests during blue-green switch" || fail "$(wc -l < "$errors") failed requests during switch: $(sort "$errors" | uniq -c | tr '\n' ' ')"
curl -fsk https://localhost/api/v1/system/status | grep -q '"version":"v2"' && pass "backend v2 live" || fail "backend v2"
ls backups/*pre-release-v2.dump >/dev/null 2>&1 && pass "pre-release backup taken and restore-checked" || fail "pre-release backup"

echo "rollback"
./rollback.sh backend >>"$LOG" 2>&1
curl -fsk https://localhost/api/v1/system/status | grep -q '"version":"v1"' && pass "rolled back to v1" || fail "rollback"
./rollback.sh backend >>"$LOG" 2>&1
curl -fsk https://localhost/api/v1/system/status | grep -q '"version":"v2"' && pass "rolled forward to v2" || fail "roll forward"

echo "release that never gets ready"
if READY_TIMEOUT=20 IMAGE=sijill-frontend-stub:f1 ./deploy.sh backend v3-broken >>"$LOG" 2>&1; then
  fail "broken release was accepted"
fi
curl -fsk https://localhost/api/v1/system/status | grep -q '"version":"v2"' && pass "broken release refused, v2 still live" || fail "live colour disturbed"
[ "$(cat state/backend.version)" = v2 ] && pass "state unchanged" || fail "state changed"
# Put a good release back in the idle colour so later checks start clean.
IMAGE="$IMAGE" ./deploy.sh backend v4 >>"$LOG" 2>&1

echo "frontend switch"
IMAGE=sijill-frontend-stub:f2 ./deploy.sh frontend f2 >>"$LOG" 2>&1
curl -fsk https://localhost/login | grep -q 'frontend f2' && pass "frontend f2 live" || fail "frontend f2"

echo "maintenance page"
./page.sh on >>"$LOG" 2>&1
code=$(curl -sk -o "$T/page" -w '%{http_code}' https://localhost/cap-table)
[ "$code" = 503 ] && grep -q 'Sijill is being updated' "$T/page" && pass "maintenance page served with 503" || fail "maintenance page ($code)"
./page.sh off >>"$LOG" 2>&1
curl -fsk https://localhost/login | grep -q 'frontend f2' && pass "page off restores traffic" || fail "page off"

echo "client notice and flags"
start=$(date -u -d '+3 days' +%Y-%m-%dT%H:%M:%SZ); end=$(date -u -d '+3 days 30 minutes' +%Y-%m-%dT%H:%M:%SZ)
./notice.sh schedule "$start" "$end" "Planned update" "تحديث مخطط" >>"$LOG" 2>&1
./notice.sh list | grep -q 'Planned update' && pass "maintenance window announced in status API" || fail "notice"
./notice.sh flag release.test on >>"$LOG" 2>&1
./notice.sh flags | grep -q '"release.test":true' && pass "feature flag switched without deploy" || fail "flag"
code=$(curl -sk -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H 'CF-Connecting-IP: 8.8.8.8' --data '{"enabled":false}' https://localhost/api/v1/system/flags/release.test)
[ "$code" = 401 ] && pass "operator write without token refused ($code)" || fail "operator write without token gave $code"

echo "backup and restore drill"
docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec -T backup backup.sh daily | tail -n 3
ls backups/*-daily.dump >/dev/null && pass "daily backup written" || fail "daily backup"
docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec -T backup sh -c \
  'restore-drill.sh "$(ls -t /backups/*-daily.dump | head -n 1)"' | grep -q 'restore drill OK' \
  && pass "restore drill on the latest dump" || fail "restore drill"

bk() { docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec -T "$@"; }
bk backup age-keygen -o /tmp/drill.key 2>/dev/null
recipient=$(bk backup age-keygen -y /tmp/drill.key)
bk -e AGE_RECIPIENT="$recipient" -e RESTORE_CHECK=0 backup backup.sh encrypted >>"$LOG" 2>&1
latest=$(ls -t backups/*-encrypted.dump.age 2>/dev/null | head -n 1)
[ -n "$latest" ] && [ ! -e "${latest%.age}" ] && pass "encrypted dump written, no plain copy left" || fail "encryption"
bk -e AGE_IDENTITY_FILE=/tmp/drill.key backup restore-drill.sh "/backups/$(basename "$latest")" | grep -q 'restore drill OK' \
  && pass "encrypted dump restores with the private key" || fail "encrypted restore"
if bk backup restore-drill.sh "/backups/$(basename "$latest")" >/dev/null 2>&1; then fail "encrypted dump restored without key"; fi
pass "encrypted dump is unreadable without the key"

echo "restore over the live database"
daily=$(ls -t backups/*-daily.dump | head -n 1)
./notice.sh flag after.backup on >>"$LOG" 2>&1
CONFIRM=yes ./restore-live.sh "$daily" >>"$LOG" 2>&1 || fail "restore-live"
flags=$(./notice.sh flags)
[[ "$flags" == *'"release.test":true'* && "$flags" != *after.backup* ]] && pass "live database restored to the dump" || fail "restore-live content: $flags"
ls backups/*-before-restore.dump* >/dev/null 2>&1 && pass "safety dump taken before restoring" || fail "safety dump"
curl -fsk https://localhost/login | grep -q 'frontend f2' && pass "maintenance page lifted after restore" || fail "page after restore"

./status.sh >/dev/null && pass "status.sh runs"
echo "all checks passed"
