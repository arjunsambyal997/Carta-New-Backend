#!/usr/bin/env python3
"""Builds the Sijill analytics dashboard in a fresh (or existing) Metabase, through its HTTP API. Safe to re-run:
it finds the database, questions and dashboard by name and updates them.

  MB_URL=https://analytics.example.com MB_ADMIN_EMAIL=you@example.com MB_ADMIN_PASSWORD=... \
  ANALYTICS_DB_PASSWORD=... python3 deploy/analytics/setup-metabase.py

On first run it also creates the Metabase admin account with that email and password. It prints the two values the
backend needs (METABASE_DASHBOARD_ID, METABASE_EMBEDDING_SECRET) for .env.

Every question filters on {{organization_id}}, and the dashboard is published for signed embedding with that filter
locked, so the backend's signed link decides which company's rows a viewer sees.
"""
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

MB = os.environ.get("MB_URL", "http://localhost:3000").rstrip("/")
EMAIL = os.environ["MB_ADMIN_EMAIL"]
PASSWORD = os.environ["MB_ADMIN_PASSWORD"]
DB = {
    "host": os.environ.get("ANALYTICS_DB_HOST", "db"),
    "port": int(os.environ.get("ANALYTICS_DB_PORT", "5432")),
    "dbname": os.environ.get("ANALYTICS_DB_NAME", "equigcc"),
    "user": os.environ.get("ANALYTICS_DB_USER", "sijill_analytics"),
    "password": os.environ["ANALYTICS_DB_PASSWORD"],
    "ssl": False,
    "schema-filters-type": "inclusion",
    "schema-filters-patterns": "analytics",
}
DB_NAME = "Sijill register"
DASHBOARD = "Company overview"
ORG = "organization_id"

session = None


def api(method, path, body=None):
    req = urllib.request.Request(MB + "/api" + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if session:
        req.add_header("X-Metabase-Session", session)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> {e.code}: {e.read().decode()[:500]}")


def items(result):
    return result["data"] if isinstance(result, dict) and "data" in result else result


# Each question: name, chart type, SQL ({{organization_id}} is the locked filter), size on a 24-column grid.
QUESTIONS = [
    ("Shares in issue", "scalar",
     "SELECT coalesce(sum(shares), 0) AS shares FROM analytics.holdings WHERE organization_id = {{organization_id}}::uuid", 6, 3),
    ("Shareholders", "scalar",
     "SELECT count(DISTINCT shareholder_id) AS shareholders FROM analytics.holdings WHERE organization_id = {{organization_id}}::uuid", 6, 3),
    ("Capital raised", "scalar",
     "SELECT coalesce(sum(consideration), 0) AS capital FROM analytics.issuances "
     "WHERE organization_id = {{organization_id}}::uuid AND status <> 'CANCELLED'", 6, 3),
    ("Open resolutions", "scalar",
     "SELECT count(*) AS open FROM analytics.resolutions WHERE organization_id = {{organization_id}}::uuid AND status = 'ACTIVE'", 6, 3),
    ("Ownership by shareholder", "pie",
     "SELECT shareholder, sum(shares) AS shares FROM analytics.holdings WHERE organization_id = {{organization_id}}::uuid "
     "GROUP BY shareholder ORDER BY shares DESC", 12, 7),
    ("Shares by class", "bar",
     "SELECT share_class, sum(shares) AS shares, sum(votes) AS votes FROM analytics.holdings "
     "WHERE organization_id = {{organization_id}}::uuid GROUP BY share_class ORDER BY shares DESC", 12, 7),
    ("Shares in issue over time", "line",
     "SELECT month, sum(issued) OVER (ORDER BY month) AS shares FROM ("
     " SELECT date_trunc('month', date_issued)::date AS month, sum(quantity) AS issued FROM analytics.issuances"
     " WHERE organization_id = {{organization_id}}::uuid AND status <> 'CANCELLED' GROUP BY 1) m ORDER BY month", 12, 7),
    ("Capital raised by month", "bar",
     "SELECT date_trunc('month', date_issued)::date AS month, sum(consideration) AS capital FROM analytics.issuances "
     "WHERE organization_id = {{organization_id}}::uuid AND status <> 'CANCELLED' GROUP BY 1 ORDER BY 1", 12, 7),
    ("Resolutions by outcome", "bar",
     "SELECT status, count(*) AS resolutions FROM analytics.resolutions WHERE organization_id = {{organization_id}}::uuid "
     "GROUP BY status ORDER BY resolutions DESC", 12, 6),
    ("Register activity, last 90 days", "line",
     "SELECT day, sum(events) AS events FROM analytics.activity WHERE organization_id = {{organization_id}}::uuid "
     "AND day >= current_date - 90 GROUP BY day ORDER BY day", 12, 6),
]


def main():
    global session
    props = api("GET", "/session/properties")
    if not props.get("has-user-setup"):
        print("Creating the Metabase admin account")
        api("POST", "/setup", {
            "token": props["setup-token"],
            "user": {"email": EMAIL, "password": PASSWORD, "first_name": "Sijill", "last_name": "Admin",
                     "site_name": "Sijill analytics"},
            "prefs": {"site_name": "Sijill analytics", "site_locale": "en", "allow_tracking": False},
        })
    session = api("POST", "/session", {"username": EMAIL, "password": PASSWORD})["id"]

    db = next((d for d in items(api("GET", "/database")) if d["name"] == DB_NAME), None)
    if db:
        db_id = db["id"]
        api("PUT", f"/database/{db_id}", {"details": DB})
    else:
        db_id = api("POST", "/database", {"engine": "postgres", "name": DB_NAME, "details": DB})["id"]
    api("POST", f"/database/{db_id}/sync_schema")
    print(f"Database '{DB_NAME}' = {db_id}")

    # Signed ("static") embedding. The setting was called enable-embedding before Metabase 0.52.
    for key in ("enable-embedding-static", "enable-embedding"):
        try:
            api("PUT", f"/setting/{key}", {"value": True})
            break
        except SystemExit:
            continue
    else:
        sys.exit("Could not turn on signed embedding")
    secret = api("GET", "/setting/embedding-secret-key")
    if not secret:
        secret = uuid.uuid4().hex + uuid.uuid4().hex
        api("PUT", "/setting/embedding-secret-key", {"value": secret})

    cards = {c["name"]: c for c in items(api("GET", "/search?models=card")) if not c.get("archived")}
    card_ids = []
    for name, display, sql, _, _ in QUESTIONS:
        body = {
            "name": name,
            "display": display,
            "visualization_settings": {},
            "dataset_query": {
                "type": "native",
                "database": db_id,
                "native": {
                    "query": sql,
                    "template-tags": {ORG: {"id": str(uuid.uuid5(uuid.NAMESPACE_URL, "sijill/" + name)), "name": ORG,
                                            "display-name": "Organization", "type": "text", "required": True}},
                },
            },
        }
        if name in cards:
            api("PUT", f"/card/{cards[name]['id']}", body)
            card_ids.append(cards[name]["id"])
        else:
            card_ids.append(api("POST", "/card", body)["id"])
    print(f"Questions: {len(card_ids)}")

    dash = next((d for d in items(api("GET", "/search?models=dashboard")) if d["name"] == DASHBOARD and not d.get("archived")), None)
    param = {"id": "org", "slug": ORG, "name": "Organization", "type": "string/=", "sectionId": "string"}
    if not dash:
        dash = api("POST", "/dashboard", {"name": DASHBOARD, "parameters": [param]})
    dashcards, row, col = [], 0, 0
    for i, ((name, _, _, w, h), card_id) in enumerate(zip(QUESTIONS, card_ids)):
        if col + w > 24:
            row, col = row + h, 0
        dashcards.append({"id": -(i + 1), "card_id": card_id, "row": row, "col": col, "size_x": w, "size_y": h,
                          "parameter_mappings": [{"parameter_id": "org", "card_id": card_id,
                                                  "target": ["variable", ["template-tag", ORG]]}]})
        col += w
    api("PUT", f"/dashboard/{dash['id']}", {"parameters": [param], "dashcards": dashcards,
                                             "enable_embedding": True, "embedding_params": {ORG: "locked"}})
    print(f"Dashboard '{DASHBOARD}' = {dash['id']}\n")
    print("Add these to /opt/sijill/.env, then redeploy the backend:")
    print(f"METABASE_DASHBOARD_ID={dash['id']}")
    print(f"METABASE_EMBEDDING_SECRET={secret}")


if __name__ == "__main__":
    main()
