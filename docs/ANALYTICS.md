# Analytics dashboard

The app's **Analytics** page shows a Metabase dashboard of the company's own register: shares in issue, shareholders,
capital raised, ownership by holder and by class, issuance over time, resolutions and register activity.

## How a company sees only its own numbers

1. Migration V19 adds the `analytics` schema: views over the register with `organization_id` on every row and no
   personal identifiers (passport, national ID, tax ID, address, date of birth), credentials or documents.
2. Metabase connects as `sijill_analytics`. That role can read those views and nothing else, in read-only
   transactions with a 30 s statement timeout.
3. Every question filters on `{{organization_id}}`. The dashboard is published for signed embedding with that
   filter **locked**.
4. The app calls `GET /api/v1/organizations/{orgId}/analytics/embed`, which works for founders, secretaries, board
   members and auditors of that company. The backend signs a one-hour link (the page fetches a new one before it runs out) with the company's id and the
   embedding secret. Metabase rejects a link that has been changed or has expired.

Shareholders don't get the page.

## Turning it on (production)

On the server, in `/opt/sijill`:

1. Add a DNS record for the analytics address (for example `analytics.example.com`, proxied by Cloudflare). If you
   use a Cloudflare Origin certificate, it must also cover this name.
2. In `.env`, set `COMPOSE_PROFILES=analytics`, `ANALYTICS_DOMAIN`, two passwords
   `METABASE_DB_PASSWORD` and `ANALYTICS_DB_PASSWORD` (`openssl rand -hex 24` each), and
   `METABASE_SESSION_SECRET` (`openssl rand -hex 32`).
3. Create the database roles: `analytics/init-db.sh`
4. Start Metabase and publish its site:
   `cp analytics/analytics.caddy sites.d/`
   `docker compose -p sijill -f docker-compose.prod.yml --env-file .env up -d metabase proxy`
   The first start takes about two minutes.
5. Build the dashboard:
   `MB_URL=https://$ANALYTICS_DOMAIN MB_ADMIN_EMAIL=you@example.com MB_ADMIN_PASSWORD='a long password' ANALYTICS_DB_PASSWORD=... python3 analytics/setup-metabase.py`
   On the first run this creates the Metabase admin account. Keep that password in your password manager.
6. Put the two printed lines (`METABASE_DASHBOARD_ID`, `METABASE_EMBEDDING_SECRET`) in `.env` and redeploy the
   backend: `./deploy.sh backend <current version>`.
7. In the frontend repository, set the Actions variable `ANALYTICS_URL` to `https://<ANALYTICS_DOMAIN>` and cut a
   release. The browser only allows frames from that address.

You can edit the dashboard in Metabase as the admin. Any new question needs the same `{{organization_id}}` filter,
mapped to the dashboard's Organization filter. A question without it would show every company's rows.

## Notes

- Metabase keeps its settings in the `metabase` database, which the nightly backup does not include. Re-running
  `setup-metabase.py` rebuilds the standard dashboard.
- Memory: Metabase is capped at 1.5 GB (`METABASE_MEM_LIMIT`). That fits next to Postgres and two backend colours on
  the 12 GB Always Free VM.
- Turning it off: clear `COMPOSE_PROFILES`, remove `sites.d/analytics.caddy`, and run
  `docker compose ... stop metabase && docker compose ... restart proxy`. The app then shows its "not connected"
  message.
