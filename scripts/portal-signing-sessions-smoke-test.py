#!/usr/bin/env python3
"""Shareholder portal, e-signatures, refresh tokens and server-side sign-out against a running app. Takes about 90 seconds
because each signature needs a fresh authenticator code."""
import datetime, time
from smoke_lib import *

EMAIL = f"f{SUFFIX}@smoke.ae"
PASSWORD = "correct horse battery"


def fresh(secret, last):
    """Waits for an authenticator step newer than `last`; returns (code, step)."""
    while int(time.time()) // 30 <= last:
        time.sleep(1)
    return totp(secret), int(time.time()) // 30


org, tok0, secret = new_org()
step = int(time.time()) // 30
p = f"/organizations/{org}"
cls = share_class(org, tok0, "Ordinary")
layla = holder(org, tok0, "Layla Mansour", email="layla@example.com")
karim = holder(org, tok0, "Karim Aziz")
c1 = issue(org, tok0, layla["id"], cls["id"], 700_000)
c2 = issue(org, tok0, karim["id"], cls["id"], 300_000)

# ---- sessions: login with MFA gives a refresh token; rotation; replay ends the session; sign-out is immediate
s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD, "organizationId": org})
check("login asks for the code", s == 200 and r["status"] == "MFA_REQUIRED", (s, r))
code, step = fresh(secret, step)
s, r = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": code})
check("verify gives access and refresh tokens", s == 200 and r["accessToken"] and r["refreshToken"], (s, r))
access, refresh = r["accessToken"], r["refreshToken"]
s, me = call("GET", "/auth/me", token=access)
check("access token works", s == 200 and me["mfaVerified"], (s, me))
s, r2 = call("POST", "/auth/refresh", {"refreshToken": refresh})
check("refresh rotates both tokens", s == 200 and r2["refreshToken"] != refresh, (s, r2))
access2, refresh2 = r2["accessToken"], r2["refreshToken"]
s, _ = call("POST", "/auth/refresh", {"refreshToken": refresh})
check("replayed refresh token refused", s == 401, s)
s, _ = call("GET", "/auth/me", token=access2)
check("replay ended the whole session", s == 401, s)
s, _ = call("POST", "/auth/refresh", {"refreshToken": refresh2})
check("newest refresh token is dead too", s == 401, s)

code, step = fresh(secret, step)
s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD, "organizationId": org})
s, r = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": code})
tok, refresh = r["accessToken"], r["refreshToken"]
s, _ = call("POST", "/auth/logout", token=tok)
check("logout", s == 204, s)
s, _ = call("GET", "/auth/me", token=tok)
check("signed-out token stops working at once", s == 401, s)
s, _ = call("POST", "/auth/refresh", {"refreshToken": refresh})
check("signed-out refresh token stops working", s == 401, s)

# ---- a fresh session for the founder (also proves password-change keeps the caller signed in)
code, step = fresh(secret, step)
s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD, "organizationId": org})
s, r = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": code})
tok = r["accessToken"]

# ---- resolution signature
deadline = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%SZ")
s, res = call("POST", f"{p}/resolutions", {"title": "Approve budget", "threshold": "ORDINARY", "quorumPct": 50, "votingDeadline": deadline}, tok)
assert s == 201, (s, res)
s, r = call("POST", f"{p}/signatures", {"documentType": "RESOLUTION", "documentRef": res["id"], "code": "000000"}, tok)
check("wrong code cannot sign", s == 401, (s, r))
code, step = fresh(secret, step)
s, r = call("POST", f"{p}/signatures", {"documentType": "RESOLUTION", "documentRef": res["id"], "code": code, "statement": "Approved as drafted"}, tok)
check("founder signs the resolution", s == 201 and r["valid"] and r["signerRole"] == "FOUNDER", (s, r))
code, step = fresh(secret, step)
s, r = call("POST", f"{p}/signatures", {"documentType": "RESOLUTION", "documentRef": res["id"], "code": code}, tok)
check("cannot sign twice", s == 422, (s, r))
s, lst = call("GET", f"{p}/signatures?documentType=RESOLUTION&documentRef={res['id']}", token=tok)
check("signature list", s == 200 and len(lst) == 1 and lst[0]["valid"] and not lst[0]["documentChanged"], (s, lst))

# ---- shareholder portal: invite Layla as a SHAREHOLDER account, enroll, look at her own holdings, sign her certificate
s, inv = call("POST", f"{p}/members", {"email": f"layla{SUFFIX}@example.com", "displayName": "Layla Mansour", "role": "SHAREHOLDER", "shareholderId": layla["id"]}, tok)
check("invite shareholder", s in (200, 201) and inv.get("temporaryPassword"), (s, inv))
s, r = call("POST", "/auth/login", {"email": f"layla{SUFFIX}@example.com", "password": inv["temporaryPassword"]})
check("holder first login needs enrollment", s == 200 and r["status"] == "OK" and r["mfaEnrollmentRequired"], (s, r))
htok = r["accessToken"]
s, enr = call("POST", "/auth/mfa/enroll", token=htok)
hcode, hstep = fresh(enr["secret"], 0)
s, r = call("POST", "/auth/mfa/confirm", {"code": hcode}, htok)
check("holder enrolls", s == 200 and r["refreshToken"], (s, r))
htok = r["accessToken"]
s, sm = call("GET", "/portal/summary", token=htok)
check("portal summary shows only her holdings", s == 200 and sm["position"]["issuedShares"] == 700_000 and [c["certificateNo"] for c in sm["certificates"]] == [c1["certificateNo"]]
      and sm["openVotes"] == [], (s, sm))
s, _ = call("GET", "/portal/summary", token=tok)
check("staff have no portal", s == 403, s)
s, _ = call("GET", f"{p}/cap-table", token=htok)
check("shareholder cannot read the cap table", s == 403, s)
hcode, hstep = fresh(enr["secret"], hstep)
s, r = call("POST", f"{p}/signatures", {"documentType": "CERTIFICATE", "documentRef": c2["certificateNo"], "code": hcode}, htok)
check("cannot sign someone else's certificate", s == 404, (s, r))
hcode, hstep = fresh(enr["secret"], hstep)
s, r = call("POST", f"{p}/signatures", {"documentType": "CERTIFICATE", "documentRef": c1["certificateNo"], "code": hcode, "statement": "I acknowledge these shares"}, htok)
check("holder signs her own certificate", s == 201 and r["valid"] and r["signerRole"] == "SHAREHOLDER", (s, r))

# ---- password change keeps this session and ends the others
code, step = fresh(secret, step)
s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD, "organizationId": org})
s, other = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": code})
s, _ = call("POST", "/auth/password/change", {"currentPassword": PASSWORD, "newPassword": "a brand new long passphrase"}, tok)
check("change password", s == 204, s)
s, _ = call("GET", "/auth/me", token=tok)
check("the session that changed it stays signed in", s == 200, s)
s, _ = call("GET", "/auth/me", token=other["accessToken"])
check("other sessions are signed out", s == 401, s)
print("ALL PASSED")
