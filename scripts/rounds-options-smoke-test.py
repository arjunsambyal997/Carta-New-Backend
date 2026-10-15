#!/usr/bin/env python3
"""Funding round simulator, option vesting and exercise, and email notices against a running app."""
import datetime, subprocess, time
from smoke_lib import *

org, tok, _ = new_org()
p = f"/organizations/{org}"
cls = share_class(org, tok, "Ordinary")
fatima = holder(org, tok, "Fatima Al Noor", email="fatima@example.com")
sami = holder(org, tok, "Sami Haddad")
issue(org, tok, fatima["id"], cls["id"], 600_000)
issue(org, tok, sami["id"], cls["id"], 400_000)

# ---- round simulator
s, r = call("POST", f"{p}/rounds/simulate", {"preMoney": 9_000_000, "investment": 1_000_000, "poolPct": 10, "investorName": "Gulf Fund"}, tok)
check("simulate round", s == 200 and abs(float(r["investorPct"]) - 10) < 0.01 and r["newPoolShares"] > 0, (s, r))
check("simulate rows include investor and pool", [x["name"] for x in r["rows"]][-2:] == ["Option pool (new)", "Gulf Fund"], r["rows"])
s, r2 = call("POST", f"{p}/rounds/simulate", {"preMoney": 1, "investment": 9, "poolPct": 50}, tok)
check("pool too large rejected", s == 422, (s, r2))
s, cap = call("GET", f"{p}/cap-table", token=tok)
check("simulation saved nothing", cap["totalIssuedShares"] == 1_000_000, cap)

# ---- options and vesting
opt = call("POST", f"{p}/certificates", {"shareholderId": sami["id"], "shareClassId": cls["id"], "instrumentType": "OPTION", "quantity": 48_000,
        "pricePerShare": 1, "currency": "AED", "dateIssued": "2024-01-01"}, tok)[1]
no = opt["certificateNo"]
s, r = call("PUT", f"{p}/certificates/{no}/vesting", {"startDate": "2024-01-01", "cliffMonths": 12, "vestingMonths": 48}, tok)
check("set vesting", s == 200 and r["granted"] == 48_000 and r["vested"] > 0, (s, r))
vested = r["vested"]
s, r = call("PUT", f"{p}/certificates/{no}/vesting", {"startDate": "2024-01-01", "cliffMonths": 12, "vestingMonths": 48}, tok)
check("second schedule rejected", s == 422, (s, r))
s, r = call("POST", f"{p}/certificates/{no}/exercise", {"quantity": 48_000}, tok)
check("cannot exercise unvested options", s == 422, (s, r))
s, r = call("POST", f"{p}/certificates/{no}/exercise", {"quantity": 10_000, "date": datetime.date.today().isoformat()}, tok)
check("exercise vested options", s == 201 and sorted(c["instrumentType"] for c in r) == ["OPTION", "SHARES"], (s, r))
s, lst = call("GET", f"{p}/vesting", token=tok)
row = lst[0]
check("schedule follows the remainder certificate", s == 200 and row["exercised"] == 10_000 and row["exercisable"] == vested - 10_000 and row["certificateNo"] != no, (s, row))
s, cap = call("GET", f"{p}/cap-table", token=tok)
check("cap table: 10000 new shares, options now 38000", cap["totalIssuedShares"] == 1_010_000 and cap["totalFullyDilutedShares"] == 1_048_000, cap)
s, r = call("POST", f"{p}/certificates/{no}/exercise", {"quantity": 1}, tok)
check("retired certificate cannot be exercised again", s == 422, (s, r))

# ---- email notices (log mailer): opening a vote notifies holders with an email
deadline = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%SZ")
s, res = call("POST", f"{p}/resolutions", {"title": "Approve budget", "threshold": "ORDINARY", "quorumPct": 50, "votingDeadline": deadline}, tok)
assert s == 201, (s, res)
s, r = call("POST", f"{p}/resolutions/{res['id']}/open", token=tok)
check("open resolution", s == 200, (s, r))
time.sleep(2)
log = open("/tmp/backend.log").read()
check("vote-open notice logged for the holder", "NOTICE (mail not configured) to fatima@example.com: Vote open: Approve budget" in log)
print("ALL PASSED")
