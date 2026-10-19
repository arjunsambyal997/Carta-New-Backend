# Deploying Sijill on one server

This sets up HTTPS, the app, the database and a daily backup on a single Linux server with Docker. It has been checked
with `docker compose config` and the backup and restore scripts have been run against a real database, but the full stack
has not been started on a server yet: expect to fix small things the first time.

## You need

- A server with Docker and Docker Compose, ports 80 and 443 open.
- A domain name with a DNS record pointing at the server (Caddy gets the HTTPS certificate by itself).
- An SMTP account for outgoing email. Production refuses to start without one.
- Both repositories side by side: `Carta-New-Backend` and `Carta-New-Frontend`.

## Steps

1. `cd Carta-New-Backend/deploy && cp .env.example .env`
2. Fill in `.env`. Generate the secrets with the commands written in the file. **Copy `ENCRYPTION_KEY` somewhere safe, off the server.** It protects every user's authenticator secret; if it is lost nobody can sign in again.
3. `docker compose -f docker-compose.prod.yml --env-file .env up -d --build`
4. Open `https://your-domain`, create the organisation and set up two-factor sign-in.
5. Check the first backup landed: `ls backups/`

## Backups

The `backup` container writes a compressed dump to `BACKUP_PATH` (default `deploy/backups`) when it starts and then every
24 hours, checks that the dump can be read back, and removes dumps older than `BACKUP_RETENTION_DAYS` (default 30).

A backup on the same disk does not survive losing the server. Copy the folder off the server (object storage, another
machine) on a schedule, for example `rclone sync deploy/backups remote:equigcc-backups`.

**Practise restoring before you need to.** Restore into a spare database and look at it:

```
docker compose -f docker-compose.prod.yml exec db createdb -U equigcc equigcc_restore
docker compose -f docker-compose.prod.yml exec -T -e PGUSER=equigcc -e PGDATABASE=equigcc_restore db \
  sh -c 'pg_restore --clean --if-exists --no-owner --single-transaction -d equigcc_restore' < backups/equigcc-XXXX.dump
```

To restore over production, stop the backend first (`docker compose ... stop backend`), restore into `equigcc`, start it again.
The audit trail is a hash chain, so restore a whole dump; never edit rows by hand.

## Updating

```
git pull   # in both repositories
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Database changes apply by themselves on start (Flyway). Take a backup first: `docker compose ... exec backup sh /backup.sh`.

## Settings worth knowing

| Setting | Default | What it does |
|---|---|---|
| `ACCESS_TOKEN_TTL` | `PT15M` | Life of a sign-in token. The app refreshes it quietly. |
| `REFRESH_TOKEN_TTL` | `P14D` | How long someone can stay signed in without using the app. |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `30` | Sign-in, sign-up, code, password and refresh calls per client per minute. |
| `RATE_LIMIT_API_PER_MINUTE` | `600` | All other API calls per client per minute. |
| `RATE_LIMIT_TRUST_FORWARDED` | `false` | `true` behind a proxy that sets `X-Forwarded-For` (the production stack sets it). Never `true` when the backend is reachable directly. |
| `NOTICES_ENABLED` | `true` | Email notices for votes and certificates. |

Rate limits are counted in each backend process. If you run more than one backend copy, each counts for itself.

## Not covered here

Monitoring and alerting, log shipping, running several backend copies, database replicas and point-in-time recovery.
The daily dump means you can lose up to a day of changes; if that is too much, add WAL archiving or a managed Postgres.
