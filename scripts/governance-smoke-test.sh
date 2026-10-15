#!/usr/bin/env bash
# End-to-end board vote against a running app. Run scripts/smoke-test.sh first (it seeds the company and
# two shareholders). Needs psql access as a role that may alter triggers, e.g.:
#   PGHOST=/tmp PGUSER=postgres ./scripts/governance-smoke-test.sh
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
SECRET=${JWT_SECRET:-change-me-change-me-change-me-change-me-0123456789}
DB=${DB:-equigcc}
ORG=11111111-1111-1111-1111-111111111111
FATIMA=33333333-0000-0000-0000-000000000001
GULF=33333333-0000-0000-0000-000000000002

token() { # role [shareholderId]
  ORG=$ORG SECRET=$SECRET ROLE=$1 SH=${2:-} python3 - <<'PY'
import base64, hashlib, hmac, json, os, time
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=').decode()
now = int(time.time())
claims = {"sub": os.environ["ROLE"].lower() + "@alnoor.ae", "roles": [os.environ["ROLE"]],
          "org_ids": [os.environ["ORG"]], "amr": ["pwd", "otp"], "typ": "access", "iat": now, "exp": now + 600}
if os.environ["SH"]:
    claims["shareholder_id"] = os.environ["SH"]
h = b(json.dumps({"alg": "HS256"}).encode()); p = b(json.dumps(claims).encode())
print(f"{h}.{p}." + b(hmac.new(os.environ["SECRET"].encode(), f"{h}.{p}".encode(), hashlib.sha256).digest()))
PY
}
call() { # token method path [body]
  curl -s -w ' [HTTP %{http_code}]\n' -X "$2" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    "$BASE/api/v1/organizations/$ORG$3" ${4:+-d "$4"}
}
FOUNDER=$(token FOUNDER); FATIMA_T=$(token SHAREHOLDER $FATIMA); GULF_T=$(token SHAREHOLDER $GULF)
DEADLINE=$(date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ)

echo "== Create resolution (draft)"
RES=$(curl -s -H "Authorization: Bearer $FOUNDER" -H 'Content-Type: application/json' \
  -X POST "$BASE/api/v1/organizations/$ORG/resolutions" \
  -d "{\"title\":\"Approve 2027 budget\",\"titleAr\":\"اعتماد ميزانية 2027\",\"threshold\":\"ORDINARY\",\"quorumPct\":50,\"votingDeadline\":\"$DEADLINE\"}" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
echo "id=$RES"
echo "== Vote on a draft (expect 422)";            call "$FATIMA_T" POST "/resolutions/$RES/votes" '{"vote":"FOR"}'
echo "== Open";                                    call "$FOUNDER" POST "/resolutions/$RES/open"
echo "== Fatima votes FOR (weight 1,000,000)";     call "$FATIMA_T" POST "/resolutions/$RES/votes" '{"vote":"FOR"}'
echo "== Fatima votes again (expect 422)";         call "$FATIMA_T" POST "/resolutions/$RES/votes" '{"vote":"AGAINST"}'
echo "== Gulf votes as Fatima (expect 403)";       call "$GULF_T" POST "/resolutions/$RES/votes" "{\"vote\":\"AGAINST\",\"shareholderId\":\"$FATIMA\"}"
echo "== Gulf votes AGAINST (weight 400,000)";     call "$GULF_T" POST "/resolutions/$RES/votes" '{"vote":"AGAINST"}'
echo "== Close (expect PASSED: 71.4% for)";        call "$FOUNDER" POST "/resolutions/$RES/close"
echo "== Audit trail";                             call "$FOUNDER" GET "/audit" | python3 -c "
import json,re,sys
t=sys.stdin.read(); body=t[:t.rindex(' [HTTP')]; d=json.loads(body)
print('chainIntact =', d['chainIntact'])
for e in reversed(d['entries']): print(' ', e['id'], e['entityName'], e['action'], '-', e['detail'][:70])"

echo "== UPDATE audit_log (expect trigger error)"
psql -d "$DB" -c "update audit_log set detail='x' where id=(select min(id) from audit_log where organization_id='$ORG')" 2>&1 | grep -m1 'append-only' || true
echo "== Tamper with the trigger disabled, then verify the chain (expect chainIntact=false)"
psql -q -d "$DB" -c "alter table audit_log disable trigger audit_log_no_update_delete" -c "update audit_log set detail='tampered' where id=(select min(id) from audit_log where organization_id='$ORG')" -c "alter table audit_log enable trigger audit_log_no_update_delete"
call "$FOUNDER" GET "/audit" | python3 -c "
import json,sys
t=sys.stdin.read(); d=json.loads(t[:t.rindex(' [HTTP')]); print('chainIntact =', d['chainIntact'], 'firstBrokenEntry =', d['firstBrokenEntry'])"
