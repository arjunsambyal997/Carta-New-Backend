#!/usr/bin/env python3
"""CSV import, transfers, buy-backs, search and cap table export against a running app."""
import json, urllib.request, urllib.error
from smoke_lib import *

org, tok, _ = new_org()
p = f"/organizations/{org}"
cls = share_class(org, tok, "Ordinary")


def upload(path, csv_text, dry):
    b = "----smoke"
    body = (f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="x.csv"\r\nContent-Type: text/csv\r\n\r\n{csv_text}\r\n--{b}--\r\n').encode()
    req = urllib.request.Request(BASE + f"{p}{path}?dryRun={'true' if dry else 'false'}", data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + b)
    req.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


s, t = call("GET", f"{p}/import/templates/holders", token=tok, raw=True)
check("holders template", s == 200 and b"name_en" in t, (s, t))

holders = "type,name_en,name_ar,nationality,id_number,registration_no,email\nINDIVIDUAL,Fatima Al Noor,فاطمة النور,AE,,,\nINDIVIDUAL,Sami Haddad,,LB,,,\n"
s, r = upload("/import/shareholders", holders, True)
check("dry run holders", s == 200 and r["created"] == 2 and r["dryRun"], (s, r))
s, r = call("GET", f"{p}/shareholders", token=tok)
check("dry run saved nothing", s == 200 and len(r) == 0, (s, r))
s, r = upload("/import/shareholders", holders, False)
check("import holders", s == 200 and r["created"] == 2 and not r["dryRun"], (s, r))
s, r = upload("/import/shareholders", holders, False)
check("re-import skips existing", s == 200 and r["created"] == 0 and r["skipped"] == 2, (s, r))

bad = "holder,share_class,quantity,price_per_share,date\nNobody,Ordinary,10,1,2026-01-01\nFatima Al Noor,Ordinary,abc,1,2026-01-01\n"
s, r = upload("/import/issuances", bad, False)
check("bad issuances report row errors", s == 200 and len(r["errors"]) == 2 and r["created"] == 0, (s, r))
good = "holder,share_class,quantity,price_per_share,date\nFatima Al Noor,Ordinary,600000,1.00,2026-01-15\nSami Haddad,Ordinary,400000,1.00,2026-01-15\n"
s, r = upload("/import/issuances", good, False)
check("import issuances", s == 200 and r["created"] == 2, (s, r))

s, hs = call("GET", f"{p}/shareholders", token=tok)
ids = {h["fullNameEn"]: h["id"] for h in hs}
fatima, sami = ids["Fatima Al Noor"], ids["Sami Haddad"]
omar = holder(org, tok, "Omar Saleh")["id"]

s, r = call("POST", f"{p}/transfers", {"fromShareholderId": sami, "toShareholderId": omar, "shareClassId": cls["id"], "quantity": 150000, "date": "2026-10-15"}, tok)
check("transfer", s in (200, 201), (s, r))
s, r = call("POST", f"{p}/transfers", {"fromShareholderId": sami, "toShareholderId": omar, "shareClassId": cls["id"], "quantity": 999999}, tok)
check("transfer more than held rejected", s == 422, (s, r))
s, cap = call("GET", f"{p}/cap-table", token=tok)

share_class(org, tok, "Treasury", classType="TREASURY")
s, r = call("POST", f"{p}/buybacks", {"fromShareholderId": fatima, "shareClassId": cls["id"], "quantity": 100000, "pricePerShare": 2, "date": "2026-10-15"}, tok)
check("buy-back", s in (200, 201), (s, r))
s, cap = call("GET", f"{p}/cap-table", token=tok)
check("treasury counted", cap.get("treasuryShares") == 100000, cap)

s, r = call("GET", f"{p}/search?q=omar", token=tok)
check("search finds holder", s == 200 and any(h["type"] == "shareholder" for h in r["hits"]), (s, r))
s, r = call("GET", f"{p}/search?q=o", token=tok)
check("short query is empty or rejected", s in (200, 422) and (s == 422 or r["hits"] == []), (s, r))

s, r = call("GET", f"{p}/cap-table/export?format=csv", token=tok, raw=True)
check("csv export", s == 200 and b"Fatima" in r, (s, r[:100] if isinstance(r, bytes) else r))
s, r = call("GET", f"{p}/cap-table/export?format=pdf", token=tok, raw=True)
check("pdf export", s == 200 and r[:4] == b"%PDF", (s, r[:50] if isinstance(r, bytes) else r))
print("ALL PASSED")
