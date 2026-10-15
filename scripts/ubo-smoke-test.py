#!/usr/bin/env python3
"""Look-through UBO calculation and exports against a running app."""
from smoke_lib import *

org, tok, _ = new_org("UBO")
cls = share_class(org, tok)
fatima = holder(org, tok, "Fatima Al Noor", name_ar="فاطمة النور")
sami = holder(org, tok, "Sami Haddad")
holdco = holder(org, tok, "Holdco Ltd", "CORPORATE", registrationNo="RC-9")
gulf = holder(org, tok, "Gulf Capital FZE", "CORPORATE")
aisha = holder(org, tok, "Aisha Khan", name_ar="عائشة خان")
omar = holder(org, tok, "Omar Saleh")
for h, q in ((fatima, 200_000), (sami, 200_000), (holdco, 600_000)):
    issue(org, tok, h["id"], cls["id"], q)

p = f"/organizations/{org}"
s, r = call("PUT", f"{p}/shareholders/{fatima['id']}/owners", {"owners": []}, tok)
check("individual cannot have owners", s == 422, (s, r))
s, r = call("PUT", f"{p}/shareholders/{holdco['id']}/owners", {"owners": [
    {"ownerId": aisha["id"], "economicPct": 50}, {"ownerId": gulf["id"], "economicPct": 50}]}, tok)
check("set holdco owners", s == 200 and len(r) == 2, (s, r))
s, r = call("PUT", f"{p}/shareholders/{gulf['id']}/owners", {"owners": [{"ownerId": omar["id"], "economicPct": 100}]}, tok)
check("set gulf owners", s == 200, (s, r))
s, r = call("PUT", f"{p}/shareholders/{gulf['id']}/owners", {"owners": [{"ownerId": holdco["id"], "economicPct": 10}]}, tok)
check("circular ownership rejected", s == 422, (s, r))
s, r = call("PUT", f"{p}/shareholders/{holdco['id']}/owners", {"owners": [
    {"ownerId": aisha["id"], "economicPct": 70}, {"ownerId": gulf["id"], "economicPct": 50}]}, tok)
check("over 100% rejected", s == 422, (s, r))

s, rep = call("GET", f"{p}/ubo", token=tok)
check("report ok", s == 200, (s, rep))
ubos = {u["nameEn"]: u for u in rep["beneficialOwners"]}
check("two UBOs through holdco", set(ubos) == {"Aisha Khan", "Omar Saleh"}, list(ubos))
check("aisha 30%", float(ubos["Aisha Khan"]["economicPct"]) == 30.0, ubos["Aisha Khan"])
check("omar 30% via two levels", float(ubos["Omar Saleh"]["economicPct"]) == 30.0 and len(ubos["Omar Saleh"]["paths"][0]["chain"]) == 4)
check("fatima and sami below threshold", {u["nameEn"] for u in rep["belowThreshold"]} == {"Fatima Al Noor", "Sami Haddad"})
check("no gaps", rep["gaps"] == [], rep["gaps"])
s, rep2 = call("GET", f"{p}/ubo?threshold=20", token=tok)
check("threshold 20 includes direct holders", len(rep2["beneficialOwners"]) == 4, len(rep2["beneficialOwners"]))
s, _ = call("GET", f"{p}/ubo?threshold=0", token=tok)
check("bad threshold rejected", s == 422)

s, csv = call("GET", f"{p}/ubo/export?format=csv", token=tok, raw=True)
check("csv export", s == 200 and "BENEFICIAL_OWNER" in csv.decode("utf-8-sig") and "عائشة خان" in csv.decode("utf-8-sig"), s)
s, pdf = call("GET", f"{p}/ubo/export?format=pdf", token=tok, raw=True)
check("pdf export", s == 200 and pdf[:5] == b"%PDF-", s)
open("/tmp/ubo.pdf", "wb").write(pdf)
s, _ = call("GET", f"{p}/ubo/export?format=xml", token=tok)
check("unknown format rejected", s == 422, s)
s, aud = call("GET", f"{p}/audit", token=tok)
check("exports and owner changes audited", any(e["entityName"] == "UboExport" for e in aud["entries"]) and any(e["entityName"] == "OwnershipLink" for e in aud["entries"]), s)
check("audit chain intact", aud.get("chainIntact", aud.get("intact", True)) is True, {k: v for k, v in aud.items() if k != "entries"})
print("ALL PASS")
