#!/usr/bin/env bash
# End-to-end check against a running app + local PostgreSQL.
# Seeds one UAE mainland company, mints a dev JWT (FOUNDER + TOTP), issues certificates,
# prints the cap table and verifies a certificate hash.
# Usage: PGHOST=/tmp PGUSER=postgres ./scripts/smoke-test.sh
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
SECRET=${JWT_SECRET:-change-me-change-me-change-me-change-me-0123456789}
ORG=11111111-1111-1111-1111-111111111111
CLASS_A=22222222-0000-0000-0000-00000000000a
FOUNDER=22222222-0000-0000-0000-00000000000f
FATIMA=33333333-0000-0000-0000-000000000001
GULF=33333333-0000-0000-0000-000000000002

psql -q -d "${DB:-equigcc}" <<SQL
insert into organization values ('$ORG',0,now(),now(),'Al Noor Holdings LLC','شركة النور القابضة ذ.م.م','MAINLAND_UAE','CN-1234567','AED','ALNOOR',0);
insert into share_class values ('$CLASS_A',0,now(),now(),'$ORG','Class A','الفئة أ','COMMON_VOTING',1,'AED',1,1,0,false,0,1000000);
insert into share_class values ('$FOUNDER',0,now(),now(),'$ORG','Founder Pref','أسهم المؤسسين الممتازة','PREFERRED_FOUNDER',1,'AED',10,1,1,false,1,null);
insert into shareholder (id,version,created_at,updated_at,organization_id,holder_type,full_name_en,full_name_ar,nationality) values
 ('$FATIMA',0,now(),now(),'$ORG','INDIVIDUAL','Fatima Al Mansoori','فاطمة المنصوري','AE'),
 ('$GULF',0,now(),now(),'$ORG','CORPORATE','Gulf Ventures Ltd',null,'SA');
SQL

TOKEN=$(ORG=$ORG SECRET=$SECRET python3 - <<'PY'
import base64, hashlib, hmac, json, os, time
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=').decode()
now = int(time.time())
h = b(json.dumps({"alg": "HS256"}).encode())
p = b(json.dumps({"sub": "founder@alnoor.ae", "roles": ["FOUNDER"], "org_ids": [os.environ["ORG"]],
                  "amr": ["pwd", "otp"], "iat": now, "exp": now + 600}).encode())
s = b(hmac.new(os.environ["SECRET"].encode(), f"{h}.{p}".encode(), hashlib.sha256).digest())
print(f"{h}.{p}.{s}")
PY
)
issue() { curl -s -w ' [HTTP %{http_code}]\n' -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -X POST "$BASE/api/v1/organizations/$ORG/certificates" -d "$1"; }

echo "== Issue 100,000 founder shares (10 votes each)"
issue "{\"shareholderId\":\"$FATIMA\",\"shareClassId\":\"$FOUNDER\",\"quantity\":100000,\"pricePerShare\":1}"
echo "== Issue 400,000 Class A at AED 12.50"
issue "{\"shareholderId\":\"$GULF\",\"shareClassId\":\"$CLASS_A\",\"quantity\":400000,\"pricePerShare\":12.5}"
echo "== Below par (expect 422)"
issue "{\"shareholderId\":\"$GULF\",\"shareClassId\":\"$CLASS_A\",\"quantity\":1000,\"pricePerShare\":0.5}"
echo "== Over authorized (expect 422)"
issue "{\"shareholderId\":\"$GULF\",\"shareClassId\":\"$CLASS_A\",\"quantity\":700000,\"pricePerShare\":2}"
echo "== Cap table"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/organizations/$ORG/cap-table"; echo
HASH=$(psql -At -d "${DB:-equigcc}" -c "select record_hash from share_issuance where certificate_no='ALNOOR-000001'")
echo "== Verify genuine hash (expect authentic=true)"
curl -s "$BASE/api/v1/certificates/ALNOOR-000001/verify?h=$HASH"; echo
echo "== Verify forged hash (expect authentic=false)"
curl -s "$BASE/api/v1/certificates/ALNOOR-000001/verify?h=deadbeef"; echo
