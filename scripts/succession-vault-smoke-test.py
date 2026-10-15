#!/usr/bin/env python3
"""Succession rule execution and the document vault against a running app."""
import urllib.request, uuid
from smoke_lib import *

org, tok, _ = new_org("SUC")
founder_cls = share_class(org, tok, "Founder", classType="PREFERRED_FOUNDER", votingMultiplier=10, seniorityRank=2, authorizedShares=1_000_000)
ordinary = share_class(org, tok, "Ordinary", authorizedShares=5_000_000)
fatima = holder(org, tok, "Fatima Al Noor", name_ar="فاطمة النور")
heir = holder(org, tok, "Layla Al Noor", name_ar="ليلى النور")
cert = issue(org, tok, fatima["id"], founder_cls["id"], 100_000, 2)
p = f"/organizations/{org}"

s, r = call("POST", f"{p}/succession-rules", {"name": "bad", "trigger": "CUSTOM", "fromClassId": ordinary["id"], "toClassId": ordinary["id"],
    "ratioNumerator": 1, "ratioDenominator": 1, "legalReference": "x"}, tok)
check("same class rejected", s == 422, (s, r))
s, rule = call("POST", f"{p}/succession-rules", {"name": "Founder death", "trigger": "FOUNDER_DEATH", "subjectId": fatima["id"],
    "fromClassId": founder_cls["id"], "toClassId": ordinary["id"], "ratioNumerator": 1, "ratioDenominator": 1,
    "beneficiaryId": heir["id"], "legalReference": "AoA art. 14"}, tok)
check("create rule", s == 201 and rule["status"] == "DRAFT", (s, rule))
s, plan = call("GET", f"{p}/succession-rules/{rule['id']}/preview", token=tok)
check("preview lists the conversion", s == 200 and len(plan) == 1 and plan[0]["toQuantity"] == 100000 and plan[0]["toHolderNameEn"] == "Layla Al Noor", (s, plan))
s, before = call("GET", f"{p}/cap-table", token=tok)
check("cap table before: Fatima holds all", before["holders"][0]["nameEn"] == "Fatima Al Noor")

s, res = call("POST", f"{p}/succession-rules/{rule['id']}/execute", {}, tok)
check("execute", s == 200 and len(res) == 1 and res[0]["shareClassName"] == "Ordinary" and res[0]["shareholderNameEn"] == "Layla Al Noor", (s, res))
s, again = call("POST", f"{p}/succession-rules/{rule['id']}/execute", {}, tok)
check("cannot execute twice", s == 422, (s, again))
s, after = call("GET", f"{p}/cap-table", token=tok)
check("cap table after: heir holds all, one holder", [h["nameEn"] for h in after["holders"]] == ["Layla Al Noor"] and after["totalIssuedShares"] == 100000, after["holders"])
s, certs = call("GET", f"{p}/certificates", token=tok)
st = {c["certificateNo"]: c["status"] for c in certs}
check("old certificate transferred, new issued", st[cert["certificateNo"]] == "TRANSFERRED" and st[res[0]["certificateNo"]] == "ISSUED", st)
check("price total preserved", float(res[0]["totalConsideration"]) == 200000.0, res[0])
s, v = call("GET", f"/certificates/{cert['certificateNo']}/verify")
check("old certificate verifies as TRANSFERRED", s == 200 and v["status"] == "TRANSFERRED", v)
s, pdf = call("GET", f"{p}/certificates/{cert['certificateNo']}/pdf", token=tok, raw=True)
check("superseded certificate PDF still renders", s == 200 and pdf[:5] == b"%PDF-")

# document vault
def upload(name, data, title="Articles of association", cat="ARTICLES_OF_ASSOCIATION", token=tok):
    b = uuid.uuid4().hex
    body = (f'--{b}\r\nContent-Disposition: form-data; name="title"\r\n\r\n{title}\r\n'
            f'--{b}\r\nContent-Disposition: form-data; name="category"\r\n\r\n{cat}\r\n'
            f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/pdf\r\n\r\n').encode() + data + f"\r\n--{b}--\r\n".encode()
    req = urllib.request.Request(BASE + f"{p}/vault", data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={b}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

doc = b"%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n"
s, d = upload("../../aoa.exe", doc)
check("upload stores a sanitised name", s == 201 and d["fileName"] == "aoa.pdf" and d["contentType"] == "application/pdf", (s, d))
s, bad = upload("evil.pdf", b"MZ\x90\x00\x03\x00\x00\x00")
check("executable disguised as pdf rejected", s == 422, (s, bad))
s, big = upload("big.pdf", b"%PDF-" + b"0" * (10 * 1024 * 1024))
check("oversized rejected", s in (413, 422), s)
s, lst = call("GET", f"{p}/vault", token=tok)
check("list shows one document", s == 200 and len(lst) == 1 and lst[0]["sha256"], (s, lst))
req = urllib.request.Request(BASE + f"{p}/vault/{d['id']}/content", headers={"Authorization": "Bearer " + tok})
with urllib.request.urlopen(req) as r:
    body = r.read()
    check("download returns the same bytes with hardening headers", body == doc and r.headers["X-Content-Type-Options"] == "nosniff"
          and r.headers["X-Content-SHA256"] == d["sha256"], dict(r.headers))
s, aud = call("GET", f"{p}/audit", token=tok)
names = {e["entityName"] for e in aud["entries"]}
check("audited", {"SuccessionRule", "VaultDocument", "ShareIssuance"} <= names, names)
print("ALL PASS")
