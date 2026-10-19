#!/usr/bin/env bash
# Tells clients about an update from the server, through the app's own maintenance banner and feature flags.
# Needs SYSTEM_ADMIN_TOKEN in .env. Caddy only accepts these writes from this machine.
#
#   ./notice.sh schedule <start> <end> "<English message>" "<Arabic message>" [read-only|banner-only]
#       Times are ISO-8601 UTC, e.g. 2026-10-24T22:00:00Z (= 02:00 Gulf time on the 25th).
#       The banner appears in the app 14 days before <start>. "read-only" (default) blocks writes during the window,
#       so clients can still view the register and download certificates; "banner-only" just warns.
#   ./notice.sh list                     current and upcoming windows, with their ids
#   ./notice.sh cancel <id>
#   ./notice.sh flags                    all feature flags
#   ./notice.sh flag <key> on|off        kill switch or dark launch, no deploy needed
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TOKEN="$(env_value SYSTEM_ADMIN_TOKEN)"

call() { # method path [json-body]
  local method="$1" path="$2" body="${3:-}" hdr
  [ -n "$TOKEN" ] || die "set SYSTEM_ADMIN_TOKEN in .env (openssl rand -hex 32) and redeploy the backend"
  hdr="$(mktemp)"
  trap 'rm -f "$hdr"' RETURN
  printf 'X-System-Admin-Token: %s\nContent-Type: application/json\n' "$TOKEN" > "$hdr"
  local data=()
  [ -n "$body" ] && data=(--data "$body")
  curl -fsS -k --max-time 15 --resolve "$DOMAIN:443:127.0.0.1" -X "$method" -H @"$hdr" \
    "${data[@]}" "https://$DOMAIN$path"
  echo
}

case "${1:-}" in
  schedule)
    [ $# -ge 5 ] || die "usage: $0 schedule <start> <end> <message-en> <message-ar> [read-only|banner-only]"
    ro=true
    [ "${6:-read-only}" = banner-only ] && ro=false
    body="$(python3 -c 'import json,sys; a=sys.argv; print(json.dumps({"startsAt": a[1], "endsAt": a[2],
      "messageEn": a[3], "messageAr": a[4], "readOnly": a[5] == "true"}, ensure_ascii=False))' "$2" "$3" "$4" "$5" "$ro")"
    call POST /api/v1/system/maintenance "$body"
    ;;
  list) public_get /api/v1/system/status; echo ;;
  cancel) [ $# -eq 2 ] || die "usage: $0 cancel <id>"; call DELETE "/api/v1/system/maintenance/$2" ;;
  flags) public_get /api/v1/system/flags; echo ;;
  flag)
    [ $# -eq 3 ] || die "usage: $0 flag <key> on|off"
    case "$3" in on) v=true ;; off) v=false ;; *) die "on or off" ;; esac
    call PUT "/api/v1/system/flags/$2" "{\"enabled\": $v}"
    ;;
  *) sed -n '2,15p' "$0"; exit 2 ;;
esac
