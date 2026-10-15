"""Shared helpers for the live smoke scripts: sign up a fresh organization with TOTP and call the API."""
import base64, hashlib, hmac, json, os, struct, time, urllib.error, urllib.request

BASE = os.environ.get("BASE", "http://localhost:8080") + "/api/v1"
SUFFIX = str(int(time.time() * 1000))[-8:]


def call(method, path, body=None, token=None, raw=False):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            data = r.read()
            return r.status, (data if raw else json.loads(data or b"null"))
    except urllib.error.HTTPError as e:
        data = e.read()
        try:
            return e.code, json.loads(data or b"null")
        except ValueError:
            return e.code, data


def totp(secret, offset=0):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    step = int(time.time()) // 30 + offset
    d = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    o = d[-1] & 15
    return "%06d" % ((struct.unpack(">I", d[o:o + 4])[0] & 0x7FFFFFFF) % 10**6)


def check(label, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + label, "" if cond else extra)
    if not cond:
        raise SystemExit(1)


def new_org(prefix=None):
    """Returns (org_id, mfa_verified_token, totp_secret) for a brand-new organization and founder."""
    email = f"f{SUFFIX}@smoke.ae"
    s, r = call("POST", "/auth/signup", {"email": email, "password": "correct horse battery", "displayName": "Founder",
        "organization": {"legalNameEn": "Al Noor Trading LLC", "legalNameAr": "النور للتجارة ذ.م.م", "jurisdiction": "MAINLAND_UAE",
                         "commercialLicenseNo": "CN-" + SUFFIX, "baseCurrency": "AED", "certificatePrefix": prefix or "S" + SUFFIX}})
    assert s == 200, (s, r)
    tok, org = r["accessToken"], r["organizationId"]
    s, enr = call("POST", "/auth/mfa/enroll", token=tok)
    s, r = call("POST", "/auth/mfa/confirm", {"code": totp(enr["secret"])}, tok)
    assert s == 200, (s, r)
    return org, r["accessToken"], enr["secret"]


def share_class(org, tok, name="Ordinary", name_ar=None, **kw):
    body = {"name": name, "nameAr": name_ar, "classType": "COMMON_VOTING", "parValue": 1, "currency": "AED",
            "votingMultiplier": 1, "seniorityRank": 1, "participating": False, "authorizedShares": 10_000_000}
    body.update(kw)
    s, r = call("POST", f"/organizations/{org}/share-classes", body, tok)
    assert s == 201, (s, r)
    return r


def holder(org, tok, name, kind="INDIVIDUAL", name_ar=None, **kw):
    body = {"holderType": kind, "fullNameEn": name, "fullNameAr": name_ar, "nationality": "AE"}
    body.update(kw)
    s, r = call("POST", f"/organizations/{org}/shareholders", body, tok)
    assert s == 201, (s, r)
    return r


def issue(org, tok, holder_id, class_id, qty, price=1):
    s, r = call("POST", f"/organizations/{org}/certificates", {"shareholderId": holder_id, "shareClassId": class_id,
        "quantity": qty, "pricePerShare": price, "currency": "AED", "dateIssued": "2026-10-15"}, tok)
    assert s == 201, (s, r)
    return r
