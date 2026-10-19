# Deploying Sijill

Production runs on one Oracle Cloud Always Free Arm VM behind Cloudflare:

```
browser -> Cloudflare (DNS, TLS, WAF, cache) -> Caddy on the VM -> /api/*  -> backend-blue | backend-green
                                                                -> else    -> frontend-blue | frontend-green
                                                 Postgres 16 (not exposed) <- nightly + pre-release backups -> R2 / B2
```

Releases are blue-green: the new version starts beside the old one and Caddy switches when it is ready. How to ship a
release, tell clients, roll back and restore is in [RELEASE_RUNBOOK.md](RELEASE_RUNBOOK.md). This page is the
one-time server setup.

What has been tested: the whole `deploy/` stack on a local Docker host with `DOMAIN=localhost`
(`deploy/test/bluegreen-test.sh`, also run by CI). What has not: a real Oracle VM, Cloudflare, R2/B2 uploads, the
GitHub release workflows end to end. Expect small fixes on the first real run.

## What lives where

| Path on the server | What | Who writes it |
|---|---|---|
| `/opt/sijill/*.sh`, `Caddyfile`, `docker-compose.*.yml`, `backup/`, `errors/` | Scripts and config | The release workflow (copied from this repo's `deploy/`) |
| `/opt/sijill/.env` | Secrets and settings | You, once |
| `/opt/sijill/certs/`, `tls.d/origin.caddy` | Cloudflare Origin certificate | You, once |
| `/opt/sijill/state/` | Live colour and version per component, Caddy upstreams, deploy history | `deploy.sh`, `rollback.sh` |
| `/opt/sijill/backups/` | Last 7 days of encrypted dumps | Backup job |

## Setup, in order

### 1. Accounts (free tiers)

- **Oracle Cloud** (card needed for verification). Home region close to the Gulf: Dubai, Abu Dhabi or Jeddah.
- **Cloudflare**, with the domain added (nameservers moved to Cloudflare).
- **Cloudflare R2** (10 GB free) or **Backblaze B2** for off-site backups: one private bucket, e.g. `sijill-backups`,
  and an API token limited to that bucket with read and write.
- **SMTP** for email (e.g. Brevo, Amazon SES, Zoho). Production will not start without it.
- **Uptime monitor** (UptimeRobot or Better Stack) and optionally a heartbeat URL for backups.

### 2. The VM

1. Create an instance: shape `VM.Standard.A1.Flex`, 2 OCPU, 12 GB, Ubuntu 24.04, 100 GB boot volume. If Oracle says
   "out of capacity", try another availability domain or later. Keep it busy enough: Oracle may reclaim Always Free
   VMs idle under 20% for 7 days.
2. In the VCN's security list, allow inbound TCP 80 and 443 (and 22 from your IP only).
3. On your laptop, make the key GitHub Actions will use: `ssh-keygen -t ed25519 -f sijill-deploy -N ""`.
4. Copy `deploy/bootstrap-vm.sh` to the VM and run `sudo bash bootstrap-vm.sh "$(cat sijill-deploy.pub)"`.
   It installs Docker, adds swap, opens 80/443 in the VM firewall, enables security updates and creates the `deploy`
   user owning `/opt/sijill`.

### 3. Cloudflare

1. DNS: an `A` record for your app name (e.g. `app`) to the VM's public IP, **Proxied** (orange cloud).
2. SSL/TLS > Overview: **Full (strict)**.
3. SSL/TLS > Origin Server > Create certificate for the app name. Save as `/opt/sijill/certs/origin.pem` and
   `origin.key` (chmod 600), then tell Caddy to use it (releases never overwrite this file):
   `mkdir -p /opt/sijill/tls.d && echo 'tls /etc/caddy/certs/origin.pem /etc/caddy/certs/origin.key' > /opt/sijill/tls.d/origin.caddy`
4. Optional hardening: in the Oracle security list, allow 443 only from Cloudflare's ranges, so nobody reaches the VM
   around Cloudflare.

### 4. Secrets

1. As `deploy`: `cp /opt/sijill/.env.example /opt/sijill/.env && chmod 600 /opt/sijill/.env`, then fill it in.
   Every value has its generator command next to it.
2. **On your laptop**, make the backup key: `age-keygen -o sijill-backup.key`. Put the printed public key
   (`age1...`) in `BACKUP_AGE_RECIPIENT`. Keep `sijill-backup.key` in your password manager, never on the server.
3. Store `ENCRYPTION_KEY` in the password manager too. If it is lost, nobody can sign in again.

### 5. GitHub

For **both** repos: Settings > Environments > `production` with you as required reviewer, the secrets
`DEPLOY_HOST`, `DEPLOY_USER` (`deploy`), `DEPLOY_SSH_KEY` (contents of `sijill-deploy`), `DEPLOY_KNOWN_HOSTS`
(`ssh-keyscan <vm ip>`), and the variable `PRODUCTION_URL`. Settings > Actions > General > Workflow permissions:
read and write.

### 6. First release

1. Tag the backend `v1.0.0` and approve the run. The first run also starts Caddy, Postgres and the backup job.
2. Tag the frontend `v1.0.0` (with a `1.0.0` entry in its changelog) and approve.
3. Open `https://<your domain>`, create the organisation, set up two-factor sign-in.
4. On the server: `./status.sh`, then run a backup by hand and check it reached the bucket:
   `docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec backup backup.sh daily`.
5. Point the uptime monitor at `https://<domain>/actuator/health/readiness` and `https://<domain>/login`.

## Settings worth knowing

| Setting | Default | What it does |
|---|---|---|
| `BACKEND_MEM_LIMIT` / `FRONTEND_MEM_LIMIT` | `3g` / `768m` | Per colour. Both colours run together for a minute during a deploy. |
| `BACKUP_HOUR_UTC` | `22` | Daily backup time (02:00 Gulf time). |
| `ACCESS_TOKEN_TTL` | `PT15M` | Life of a sign-in token. The app refreshes it quietly. |
| `REFRESH_TOKEN_TTL` | `P14D` | How long someone can stay signed in without using the app. |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `30` | Sign-in style calls per client per minute, shared by both colours (database store). |
| `RATE_LIMIT_API_PER_MINUTE` | `600` | All other API calls per client per minute, counted per process. |
| `NOTICES_ENABLED` | `true` | Email notices for votes and certificates. |

Deploy script knobs (environment, on the server): `READY_TIMEOUT` (300 s), `DRAIN_SECONDS` (20 s).

## Local stack

Unchanged: `docker compose up --build` from the repo root, with the frontend checked out next to it.

## Not covered yet

Staging (plan: a second compose project or VM once there is a paying client), WAL archiving for point-in-time
recovery, metrics dashboards, Sentry. The free-tier upgrade triggers are in the product plan.
