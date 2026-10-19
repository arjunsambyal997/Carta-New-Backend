#!/usr/bin/env python3
"""API keys and webhooks against a running app. Start the app with WEBHOOKS_ALLOW_INSECURE=true WEBHOOKS_POLL_MS=1000."""
import hashlib, hmac, http.server, threading, urllib.request
from smoke_lib import *

received = []


class Receiver(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"]))
        received.append((dict(self.headers), body))
        self.send_response(200)
        self.end_headers()

    def log_message(self, *a):
        pass


server = http.server.HTTPServer(("127.0.0.1", 0), Receiver)
threading.Thread(target=server.serve_forever, daemon=True).start()
hook_url = f"http://127.0.0.1:{server.server_port}/hook"

org, tok, _ = new_org("INT")
p = f"/organizations/{org}"

# API keys
s, k = call("POST", f"{p}/api-keys", {"name": "Reporting"}, tok)
check("create key", s == 201 and k["secret"].startswith("eqk_"), (s, k))
secret = k["secret"]


def with_key(method, path, key, body=None):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("X-API-Key", key)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


check("key reads cap table", with_key("GET", f"{p}/cap-table", secret) == 200)
check("key cannot write", with_key("POST", f"{p}/share-classes", secret, {}) == 403)
check("key cannot manage keys", with_key("GET", f"{p}/api-keys", secret) == 403)
check("bad key is 401", with_key("GET", f"{p}/cap-table", "eqk_" + "x" * 48) == 401)
s, other = call("POST", "/auth/signup", {"email": f"o{SUFFIX}@smoke.ae", "password": "correct horse battery", "displayName": "Other",
    "organization": {"legalNameEn": "Other LLC", "jurisdiction": "MAINLAND_UAE", "commercialLicenseNo": "CN-O" + SUFFIX,
                     "baseCurrency": "AED", "certificatePrefix": "O" + SUFFIX}})
check("key is scoped to its organization", with_key("GET", f"/organizations/{other['organizationId']}/cap-table", secret) == 403)
s, keys = call("GET", f"{p}/api-keys", token=tok)
check("list shows prefix but never the secret", s == 200 and keys[0]["keyPrefix"] and "secret" not in json.dumps(keys), keys)
check("last used recorded", keys[0]["lastUsedAt"] is not None, keys)
s, _ = call("DELETE", f"{p}/api-keys/{k['key']['id']}", token=tok)
check("revoke", s == 204, s)
check("revoked key is 401", with_key("GET", f"{p}/cap-table", secret) == 401)

# Webhooks
s, ev = call("GET", f"{p}/webhook-events", token=tok)
check("event catalogue", s == 200 and "certificate.issued" in json.dumps(ev), (s, ev))
s, r = call("POST", f"{p}/webhooks", {"url": "ftp://x", "events": ["certificate.issued"]}, tok)
check("bad url rejected", s == 422, (s, r))
s, r = call("POST", f"{p}/webhooks", {"url": hook_url, "events": ["nonsense"]}, tok)
check("unknown event rejected", s == 422, (s, r))
s, w = call("POST", f"{p}/webhooks", {"url": hook_url, "events": ["certificate.issued"]}, tok)
check("create webhook", s == 201 and w["secret"].startswith("whsec_"), (s, w))
hook_id, hook_secret = w["webhook"]["id"], w["secret"]

s, _ = call("POST", f"{p}/webhooks/{hook_id}/test", token=tok)
deadline = time.time() + 20
while not received and time.time() < deadline:
    time.sleep(0.5)
check("test ping delivered", received and json.loads(received[0][1])["type"] == "ping", received)

cls = share_class(org, tok)
fatima = holder(org, tok, "Fatima Al Noor")
cert = issue(org, tok, fatima["id"], cls["id"], 1000)
deadline = time.time() + 20
while len(received) < 2 and time.time() < deadline:
    time.sleep(0.5)
check("certificate event delivered", len(received) >= 2, len(received))
headers, body = received[-1]
evt = json.loads(body)
check("payload has envelope", evt["type"] == "certificate.issued" and evt["organizationId"] == org and evt["data"]["certificateNo"], evt)
t, v1 = [part.split("=", 1)[1] for part in headers["X-EquiGCC-Signature"].split(",")]
expected = hmac.new(hook_secret.encode(), f"{t}.{body.decode()}".encode(), hashlib.sha256).hexdigest()
check("signature verifies", hmac.compare_digest(expected, v1))

s, dl = call("GET", f"{p}/webhooks/{hook_id}/deliveries", token=tok)
check("delivery log", s == 200 and any(d["status"] == "DELIVERED" for d in dl), (s, dl))
s, r = call("PATCH", f"{p}/webhooks/{hook_id}", {"active": False}, tok)
check("pause", s == 200 and r["active"] is False, (s, r))
n = len(received)
issue(org, tok, fatima["id"], cls["id"], 10)
time.sleep(3)
check("paused webhook receives nothing", len(received) == n)
s, _ = call("DELETE", f"{p}/webhooks/{hook_id}", token=tok)
check("delete webhook", s == 204, s)
print("ALL PASS")
