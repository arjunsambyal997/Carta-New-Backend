# Release runbook

How a Sijill release reaches clients without surprising them, and how to undo it. Every command below runs as the
`deploy` user in `/opt/sijill` on the server unless it says GitHub.

The rule: **every release can be undone in minutes, and no release changes the database in a way the previous
version cannot read.**

## What happens on a release

```
git tag v1.4.0 && git push origin v1.4.0        (in each repo that changed; backend first)
  -> GitHub Actions: tests -> image ghcr.io/.../carta-new-backend:v1.4.0 (amd64 + arm64)
  -> waits for a reviewer to approve the "production" environment
  -> on the server, deploy.sh:
       pre-release backup (restore-checked, encrypted, copied off-site)
       start v1.4.0 in the idle colour beside the live one, migrations run
       wait for /actuator/health/readiness (database included)
       switch Caddy to the new colour (graceful, open requests finish)
       check the public URL reports v1.4.0, switch straight back if not
       drain and stop the old colour, keep it for rollback
```

Clients see nothing during a normal release. This was tested on a local Docker stack under steady traffic: zero
failed requests across the switch (`deploy/test/bluegreen-test.sh`, also run by CI on every pull request).

## Choose the release type

| Type | Examples | Client notice | Window |
|---|---|---|---|
| **Routine** | UI changes, new features behind a flag, additive migrations | Release notes only | Any time |
| **Planned maintenance** | Large backfill, a migration that rewrites a big table, server move | Banner + email 7 days ahead, status page | Friday night or Saturday morning Gulf time |
| **Breaking API change** | Removing or renaming an API field, webhook payload change | Email 90 days ahead, `Deprecation` header, old and new side by side | After the 90 days |
| **Emergency fix** | Security fix, data-corrupting bug | Status page incident at once, banner when live, email after | Now |

If you are unsure, treat it as planned maintenance.

## Routine release

1. **Release notes.** Add the version to `src/features/changelog/releases.ts` in the frontend repo, in English and
   Arabic, written for clients ("you can now..."). The frontend release refuses to run without it. Bump it even when
   only the backend changed, so clients see one version number.
2. **Migrations follow expand/contract** (next section). CI already fails a pull request that edits an applied
   migration.
3. **Merge to main**, wait for CI green.
4. **Tag** the backend, then the frontend, with the same version:
   `git tag v1.4.0 && git push origin v1.4.0`.
5. **Approve** each run in GitHub > Actions > Release > Review deployments.
6. **Check:** `./status.sh` on the server, open the app, open `/changelog`.
7. **New features ship dark** behind a flag, then: `./notice.sh flag <key> on`. The kill switch is the same command
   with `off`; no deploy needed.

## Planned maintenance (clients told ahead)

1. **Seven days before**, schedule the window. The app shows a banner in English and Arabic to every user from
   14 days ahead; during the window the app is read-only, so clients can still view the register and download
   certificates.
   ```
   ./notice.sh schedule 2026-10-30T22:00:00Z 2026-10-30T23:00:00Z \
     "Scheduled maintenance on 31 Oct, 02:00 to 03:00 Gulf time. The register stays viewable." \
     "صيانة مجدولة يوم 31 أكتوبر من 02:00 إلى 03:00 بتوقيت الخليج. سيبقى السجل متاحاً للعرض."
   ./notice.sh list
   ```
   Use `banner-only` as the last argument to warn without blocking writes.
2. **Same day**, email org admins (template below) and add the window to the status page.
3. **One day and one hour before**, send the reminder email.
4. **In the window**, tag and approve as in a routine release. Writes are already blocked, so a long migration
   cannot race client edits.
5. **If the app must be fully offline** (restoring a backup, moving servers), `./page.sh on` shows the bilingual
   maintenance page to everyone; `./page.sh off` ends it.
6. **After**, check `./status.sh`, cancel the window if it is not over yet (`./notice.sh cancel <id>`), send the
   completion email, close the status page entry.

### Email template

> **Subject:** Sijill scheduled maintenance on {date} / صيانة مجدولة لمنصة سجل في {date}
>
> On {date} from {start} to {end} Gulf time we will update Sijill. During this time you can view your register and
> download certificates, but changes (issuances, transfers, votes) will be paused. No action is needed from you.
> What's new: {link to /changelog}. Status: {status page link}.
>
> سنقوم بتحديث منصة سجل يوم {date} من {start} إلى {end} بتوقيت الخليج. خلال هذه الفترة يمكنكم عرض السجل وتنزيل الشهادات،
> لكن ستتوقف التعديلات مؤقتاً. لا يلزم أي إجراء منكم.

## Database migrations: expand, then contract

The old colour keeps serving while the new one migrates, and rollback runs the old version against the new schema.
So every migration must be readable by the version before it.

| Change | Release N (expand) | Release N+1 | Release N+2 (contract) |
|---|---|---|---|
| Add a column | Add it nullable or with a default | Start writing and reading it | Make it NOT NULL if needed |
| Rename a column | Add the new column, write both, backfill in batches | Read the new one | Drop the old one |
| Drop a column or table | Stop using it in code | | Drop it |
| Add an index on a big table | `CREATE INDEX CONCURRENTLY` in its own migration (mark it non-transactional) | | |

Never edit or delete an applied migration (CI and Flyway both refuse). Flyway undo is not used: fix forward with a
new migration, and the pre-release backup is the real undo. Rehearse any schema release on a restored copy of
last night's backup first (`restore-drill.sh` gives you one).

## Roll back

**The app** (seconds, no data change). The previous version is still on the server, stopped:
```
./rollback.sh backend        # or frontend
```
Run it again to roll forward. To go further back, redeploy an older tag: GitHub > Actions > Release > Run workflow
with `v1.2.0`, or on the server `./deploy.sh backend v1.2.0`. This only works because of expand/contract.

**The frontend** works the same way; the backend and frontend roll back independently.

**The data** (last resort, loses everything written since the backup). Check the dump, then restore it:
```
docker compose -p sijill -f docker-compose.prod.yml --env-file .env exec -T backup restore-drill.sh /backups/<file>
./restore-live.sh backups/<file> [path/to/age-private-key]
```
`restore-live.sh` shows the maintenance page, stops the live backend, takes a `before-restore` dump of the current
state (so the restore itself can be undone), restores, starts the backend, waits for readiness and lifts the page.
If the dump is from an older release, roll the backend back to that release afterwards.
The audit trail is a hash chain: restore whole dumps, never edit rows by hand. Pre-release dumps are named
`sijill-<time>-pre-release-<version>.dump.age`, and copies sit in `releases/` in the off-site bucket.

Encrypted dumps need the age private key, which never lives on the server: copy it up for the restore, pass its path
as the second argument, and delete it afterwards. For a drill on an encrypted dump, copy the key into the backup
container (`docker compose ... cp key backup:/tmp/key`) and add `-e AGE_IDENTITY_FILE=/tmp/key` to the `exec`.

## Backups

- Nightly at `BACKUP_HOUR_UTC` (default 22:00 UTC = 02:00 Gulf time), plus one before every backend release.
- Each dump is restored into a scratch database and checked before it is kept, so a backup that cannot be restored
  fails loudly. A missed ping on `BACKUP_PING_URL` alerts you.
- Encrypted with age to your public key, copied to R2 or B2: 30 daily, 12 monthly, release dumps for a year.
  Local copies: 7 days.
- **Monthly restore drill** (first Monday): download the newest off-site dump to your laptop or the server and run
  `restore-drill.sh` with the private key. It prints the schema version and row counts. Note the result.
- Targets: lose at most 24 hours of data (RPO), back in service within 1 hour (RTO).

## Health and alerts

| Check | Where | Alert |
|---|---|---|
| Uptime | `https://<domain>/actuator/health/readiness` and `/login` in UptimeRobot or Better Stack (free) | Phone + email |
| Backups | `BACKUP_PING_URL` heartbeat, daily | Missed ping |
| Server | `./status.sh` | |
| Logs | `docker compose -p sijill-backend-<colour> -f docker-compose.backend.yml --env-file .env --env-file state/backend-<colour>.env logs --tail 200` | |

Each container's logs rotate at 5 x 10 MB.

**Alert: site down.** `./status.sh`. If the live colour is unhealthy, `./rollback.sh backend`. If Caddy or Postgres
is down: `docker compose -p sijill -f docker-compose.prod.yml --env-file .env up -d`.
**Alert: backup missed.** `docker compose -p sijill -f docker-compose.prod.yml --env-file .env logs backup`.
Run one by hand: `... exec backup backup.sh daily`.

## One-time setup

See [DEPLOYMENT.md](DEPLOYMENT.md) for the server. In GitHub, for **each** repo:

1. Settings > Environments > New environment `production`. Add yourself under Required reviewers.
2. In that environment add secrets `DEPLOY_HOST` (server IP), `DEPLOY_USER` (`deploy`), `DEPLOY_SSH_KEY` (the private
   key whose public half you gave `bootstrap-vm.sh`), `DEPLOY_KNOWN_HOSTS` (output of `ssh-keyscan <server ip>`), and
   variable `PRODUCTION_URL` (`https://<domain>`).
3. Settings > Actions > General > Workflow permissions: read and write (to publish images).
