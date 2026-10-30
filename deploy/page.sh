#!/usr/bin/env bash
# Full maintenance page for the rare window where the app must be completely offline (restoring a backup, moving
# servers). Normal releases do not need this: they are blue-green, and the read-only banner covers risky migrations.
#   ./page.sh on    every visitor sees the bilingual maintenance page; the API answers 503
#   ./page.sh off   traffic goes back to the live colours
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

case "${1:-}" in
  on)
    write_placeholder_upstreams
    cat > "$UPSTREAMS/frontend.caddy" <<'EOF'
# Written by page.sh: maintenance page.
error 503
EOF
    cp "$UPSTREAMS/frontend.caddy" "$UPSTREAMS/backend.caddy"
    reload_proxy
    log "maintenance page is ON; run ./page.sh off when done"
    ;;
  off)
    for c in backend frontend; do
      color="$(active_color "$c")"
      [ -n "$color" ] && write_upstream "$c" "$color"
    done
    reload_proxy
    log "maintenance page is OFF"
    ;;
  *) die "usage: $0 on|off" ;;
esac
