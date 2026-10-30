#!/usr/bin/env bash
# Shared helpers for deploy.sh, rollback.sh, page.sh, notice.sh and status.sh. Sourced, not run.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
STATE_DIR="$DEPLOY_DIR/state"
UPSTREAMS="$STATE_DIR/upstreams"
READY_TIMEOUT="${READY_TIMEOUT:-300}"   # seconds; a cold JVM plus migrations on a small Arm VM can take a while
DRAIN_SECONDS="${DRAIN_SECONDS:-20}"    # old colour keeps serving open requests this long after the switch

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

[ -r "$ENV_FILE" ] || die "missing $ENV_FILE (copy .env.example and fill it in)"
mkdir -p "$STATE_DIR" "$UPSTREAMS" "$DEPLOY_DIR/certs" "$DEPLOY_DIR/backups" "$DEPLOY_DIR/sites.d"

env_value() { # read one KEY from .env without sourcing the whole file into the shell
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}
DOMAIN="$(env_value DOMAIN)"
[ -n "$DOMAIN" ] || die "DOMAIN is empty in $ENV_FILE"

base() { docker compose -p sijill -f "$DEPLOY_DIR/docker-compose.prod.yml" --env-file "$ENV_FILE" "$@"; }

color_env() { echo "$STATE_DIR/$1-$2.env"; }   # component colour

app() { # component colour compose-args...
  local component="$1" color="$2"; shift 2
  local cenv; cenv="$(color_env "$component" "$color")"
  [ -r "$cenv" ] || die "$component $color has never been deployed"
  docker compose -p "sijill-$component-$color" -f "$DEPLOY_DIR/docker-compose.$component.yml" \
    --env-file "$ENV_FILE" --env-file "$cenv" "$@"
}

port_of() { case "$1" in backend) echo 8080 ;; frontend) echo 3000 ;; *) die "unknown component $1" ;; esac; }
probe_path() { case "$1" in backend) echo /actuator/health/readiness ;; frontend) echo /login ;; esac; }
other() { [ "$1" = blue ] && echo green || echo blue; }
active_color() { cat "$STATE_DIR/$1.color" 2>/dev/null || true; }
active_version() { cat "$STATE_DIR/$1.version" 2>/dev/null || true; }

write_upstream() { # component colour
  local component="$1" color="$2" file="$UPSTREAMS/$1.caddy"
  cat > "$file.tmp" <<EOF
# Written by deploy.sh: $component traffic goes to the $color colour.
reverse_proxy $component-$color:$(port_of "$component") {
	header_up X-Forwarded-For {client_ip}
	lb_try_duration 5s
	lb_try_interval 250ms
}
EOF
  mv "$file.tmp" "$file"
}

write_placeholder_upstreams() { # before anything is deployed, keep Caddy valid: unknown hosts give the maintenance page
  for c in backend frontend; do
    [ -f "$UPSTREAMS/$c.caddy" ] || write_upstream "$c" blue
  done
}

reload_proxy() {
  base exec -T proxy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
}

ensure_base() {
  write_placeholder_upstreams
  local build=--build   # the backup image is built here from deploy/backup; SKIP_BASE_BUILD=1 reuses the existing one
  [ "${SKIP_BASE_BUILD:-0}" = 1 ] && build=""
  base up -d $build --remove-orphans proxy db backup >/dev/null
  for _ in $(seq 1 60); do
    base exec -T db pg_isready -U equigcc >/dev/null 2>&1 && return 0
    sleep 2
  done
  die "database did not become ready"
}

# Asks the colour directly over the private network (through the proxy container, which has wget) until it is ready.
wait_ready() { # component colour
  local component="$1" color="$2" url deadline
  url="http://$component-$color:$(port_of "$component")$(probe_path "$component")"
  deadline=$(( $(date +%s) + READY_TIMEOUT ))
  log "waiting for $component-$color to be ready ($url)"
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if base exec -T proxy wget -q -O /dev/null -T 5 "$url" 2>/dev/null; then
      log "$component-$color is ready"
      return 0
    fi
    sleep 3
  done
  return 1
}

# Calls the public site through Caddy on this machine (same TLS, routing and headers as a real visitor, minus Cloudflare).
public_get() { # path
  curl -fsS -k --max-time 10 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN$1"
}

with_lock() {
  exec 9>"$STATE_DIR/deploy.lock"
  # Backend and frontend releases can arrive together; the second waits its turn instead of failing.
  flock -w 900 9 || die "another deploy or rollback has held the lock for 15 minutes"
}
