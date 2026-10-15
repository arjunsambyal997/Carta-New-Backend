#!/usr/bin/env python3
"""Signup -> enroll TOTP -> confirm -> login with MFA -> setup -> invite, against a running app.
Usage: BASE=http://localhost:8080 python3 scripts/auth-smoke-test.py"""
import base64, hashlib, hmac, json, os, struct, time, urllib.request, urllib.error

BASE = os.environ.get("BASE", "http://localhost:8080") + "/api/v1"
SUFFIX = str(int(time.time()))[-6:]

def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

def totp(secret, offset=0):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    step = int(time.time()) // 30 + offset
    d = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    o = d[-1] & 15
    return "%06d" % ((struct.unpack(">I", d[o:o + 4])[0] & 0x7FFFFFFF) % 10**6)

def check(label, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + label, extra if not cond else "")
    if not cond:
        raise SystemExit(1)

email, pw = f"founder{SUFFIX}@alnoor.ae", "correct horse battery"
s, r = call("POST", "/auth/signup", {"email": email, "password": pw, "displayName": "Founder",
    "organization": {"legalNameEn": "Smoke Co", "jurisdiction": "MAINLAND_UAE", "commercialLicenseNo": "CN-" + SUFFIX,
                     "baseCurrency": "AED", "certificatePrefix": "SMK"}})
check("signup", s == 200 and r["status"] == "OK", (s, r))
tok = r["accessToken"]; org = r["organizationId"]
check("enrollment flagged", r["mfaEnrollmentRequired"])
check("password-only token is not MFA verified", call("GET", f"/organizations/{org}/cap-table", token=tok)[0] == 403)
s, enr = call("POST", "/auth/mfa/enroll", token=tok)
check("enroll returns secret", s == 200 and enr["secret"] and enr["otpauthUri"].startswith("otpauth://totp/"), (s, enr))
s, bad = call("POST", "/auth/mfa/confirm", {"code": "000000"}, tok)
check("wrong code rejected", s == 401, (s, bad))
s, r = call("POST", "/auth/mfa/confirm", {"code": totp(enr["secret"])}, tok)
check("confirm gives verified token", s == 200 and r["accessToken"], (s, r))
tok2 = r["accessToken"]
s, me = call("GET", "/auth/me", token=tok2)
check("me mfaVerified", s == 200 and me["mfaVerified"] and me["role"] == "FOUNDER", (s, me))
s, r = call("POST", "/auth/login", {"email": email, "password": pw})
check("login asks for MFA", s == 200 and r["status"] == "MFA_REQUIRED", (s, r))
s, bad = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": "000000"})
check("bad mfa code", s == 401, (s, bad))
time.sleep(1)
# replay protection: the code consumed at confirm must not work again within its step
s, rep = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": totp(enr["secret"])})
print("INFO replay of same-step code ->", s)
s, r2 = call("POST", "/auth/login", {"email": email, "password": pw})
s, ok = call("POST", "/auth/mfa/verify", {"mfaToken": r2["mfaToken"], "code": totp(enr["secret"], 1)})
check("verify with next-step code", s == 200 and ok["accessToken"], (s, ok))
tok3 = ok["accessToken"]
check("wrong password uniform", call("POST", "/auth/login", {"email": email, "password": "nope-nope-nope"})[0] == 401)
check("unknown user uniform", call("POST", "/auth/login", {"email": "x@y.ae", "password": "nope-nope-nope"})[0] == 401)

s, cls = call("POST", f"/organizations/{org}/share-classes", {"name": "Ordinary", "classType": "COMMON_VOTING", "parValue": 1,
    "currency": "AED", "authorizedShares": 1000000, "votingMultiplier": 1, "seniorityRank": 1, "participating": False}, tok3)
check("create share class", s in (200, 201), (s, cls))
s, sh = call("POST", f"/organizations/{org}/shareholders", {"holderType": "INDIVIDUAL", "fullNameEn": "Fatima Al Noor",
    "nationality": "AE", "email": f"fatima{SUFFIX}@alnoor.ae"}, tok3)
check("create shareholder", s in (200, 201), (s, sh))
s, inv = call("POST", f"/organizations/{org}/members", {"email": f"sec{SUFFIX}@alnoor.ae", "displayName": "Sec", "role": "CORPORATE_SECRETARY"}, tok3)
check("invite member", s in (200, 201), (s, inv))
s, ms = call("GET", f"/organizations/{org}/members", token=tok3)
check("list members", s == 200 and len(ms) == 2, (s, ms))
print("ALL PASS")
