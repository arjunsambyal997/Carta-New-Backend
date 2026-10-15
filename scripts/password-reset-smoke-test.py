#!/usr/bin/env python3
"""Password reset and change against a running app started without SMTP (the link is read from the log).
Usage: LOG=/tmp/backend.log python3 scripts/password-reset-smoke-test.py"""
import re, time
from smoke_lib import *

LOG = os.environ.get("LOG", "/tmp/backend.log")
org, tok, secret = new_org()
email, old = f"f{SUFFIX}@smoke.ae", "correct horse battery"

def last_token():
    time.sleep(0.5)
    links = re.findall(re.escape(email) + r".*?reset-password\?token=([A-Za-z0-9_-]+)", open(LOG).read())
    return links[-1] if links else None

s, r = call("POST", "/auth/password/forgot", {"email": "nobody@smoke.ae"})
check("unknown address answers the same", s == 202, (s, r))
s, r = call("POST", "/auth/password/forgot", {"email": email})
check("known address answers 202", s == 202, (s, r))
t1 = last_token()
check("link was produced", t1 is not None)
s, r = call("POST", "/auth/password/forgot", {"email": email})
t2 = last_token()
check("second request issues a different link", t2 and t2 != t1)
s, r = call("POST", "/auth/password/reset", {"token": t1, "newPassword": "brand new password 1"})
check("older link is dead", s == 422, (s, r))
s, r = call("POST", "/auth/password/reset", {"token": t2, "newPassword": "short"})
check("weak password rejected", s == 400, (s, r))
s, r = call("POST", "/auth/password/reset", {"token": t2, "newPassword": "brand new password 1"})
check("reset works", s == 204, (s, r))
s, r = call("POST", "/auth/password/reset", {"token": t2, "newPassword": "brand new password 2"})
check("link cannot be reused", s == 422, (s, r))
s, r = call("POST", "/auth/login", {"email": email, "password": old})
check("old password no longer works", s == 401, (s, r))
s, r = call("POST", "/auth/login", {"email": email, "password": "brand new password 1"})
check("new password works and 2FA is still required", s == 200 and r["status"] == "MFA_REQUIRED", (s, r))
s, ok = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": totp(secret, 1)})
check("signs in with the authenticator code", s == 200, (s, ok))
t = ok["accessToken"]
s, r = call("POST", "/auth/password/change", {"currentPassword": "wrong wrong wrong", "newPassword": "brand new password 3"}, t)
check("change with wrong current password rejected", s == 401, (s, r))
s, r = call("POST", "/auth/password/change", {"currentPassword": "brand new password 1", "newPassword": "brand new password 3"}, t)
check("change works", s == 204, (s, r))
s, r = call("POST", "/auth/login", {"email": email, "password": "brand new password 3"})
check("changed password works", s == 200, (s, r))
print("ALL PASS")
