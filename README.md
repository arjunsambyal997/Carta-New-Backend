# Sijill Platform

Spring Boot 3.3 / Java 21 backend for the Sijill digital share register: multi-class cap table, share
certificates, board governance, beneficial-ownership (UBO) reporting and a shareholder portal for UAE (Mainland,
ADGM, DIFC) and KSA entities. The web app lives in the separate `Carta-New-Frontend` repository and talks only to
this API.

## Package map

Packages under `com.equigcc` are organised by feature, not by layer: a feature's entities, repositories, DTOs,
services and controller live together. New code goes into the feature it belongs to; do not add `entity`, `dto`,
`service` or `util` packages. `PackageStructureTest` fails the build if one appears.

| Package | Responsibility |
|---|---|
| `organization` | Organizations (companies), jurisdictions, first-run setup, share-class and shareholder endpoints, member invites |
| `captable` | Share classes, shareholders, the cap table and its CSV export, exit waterfall, register search |
| `certificate` | Share certificates: issuance, SHA-256 record hash, PDF, public verification |
| `register` | Register tools: CSV import of shareholders and issuances, transfers, buy-backs |
| `equity` | Option vesting and exercise, funding-round simulator |
| `governance` | Board resolutions, thresholds, voting, tally |
| `signature` | Internal e-signature attestations on resolutions and certificates |
| `ownership` | Ownership links between holders (who owns whom) |
| `ubo` | Look-through beneficial-owner calculation and its CSV/PDF export |
| `succession` | Succession rules (convert or transfer on a trigger) |
| `vault` | Document vault with content hashing and type checks |
| `notice` | Email notices: vote opened, deadline reminder, certificate issued |
| `portal` | Shareholder portal summary |
| `apikey` | Read-only API keys for reporting and BI tools (`X-API-Key`) |
| `webhook` | Signed, retried outbound webhooks for register events |
| `audit` | Per-organization hash-chained audit trail |
| `auth` | Users, memberships and roles, JWT, TOTP, sessions and refresh tokens, password reset |
| `config` | Spring configuration: security chain, rate limiting, request ids, OpenAPI, clock, startup secret checks |
| `web` | Global error handling |
| `common` | Small shared helpers: base entity, business-rule exceptions, CSV parsing |

Tests mirror the package they cover (`src/test/java/com/equigcc/<feature>`).

## Build, run, test

```bash
./mvnw test        # unit and web-slice tests (no database needed)
./mvnw verify      # also writes the coverage report to target/site/jacoco
```

Swagger UI is at `/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`. Every endpoint is also in the
Postman collection under `postman/` (import both files; the Log in request stores the tokens for the rest).
Every response carries an `X-Request-Id` header, and the same id appears in the log lines for that request.
CI (`.github/workflows`) runs the tests, builds the jar and the Docker image, and validates the compose files; a
weekly dependency scan runs OWASP dependency-check.

## API

| Method | Path | Who |
|---|---|---|
| `POST` | `/api/v1/organizations/{orgId}/certificates` | `FOUNDER` or `CORPORATE_SECRETARY`, member of org, MFA session |
| `GET` | `/api/v1/organizations/{orgId}/cap-table` | founder, secretary, board member, legal auditor of that org |
| `GET` | `/api/v1/organizations/{orgId}/waterfall?exitValue=…` | same roles as the cap table |
| `POST` | `/api/v1/organizations/{orgId}/resolutions` | founder or secretary, MFA: creates a draft |
| `POST` | `…/resolutions/{id}/open` · `…/close` | founder or secretary, MFA |
| `POST` | `…/resolutions/{id}/votes` | any voter with MFA; votes as the account's linked shareholder |
| `GET` | `…/resolutions` · `…/resolutions/{id}` | org members, with live tally and recorded votes |
| `GET` | `/api/v1/organizations/{orgId}/audit` | founder, secretary, legal auditor; verifies the hash chain |
| `GET` | `/api/v1/certificates/{certificateNo}/verify?h={hash}` | public (QR target, no shareholder PII) |

Swagger UI: `/swagger-ui.html`.

Example issuance request:

```json
{ "shareholderId": "…", "shareClassId": "…", "quantity": 100000, "pricePerShare": 12.50 }
```

`instrumentType` (default `SHARES`; also `OPTION`, `WARRANT`, `CONVERTIBLE`), `currency` (default: the
class currency) and `dateIssued` (default: today, UTC) are optional.

Issuance rules enforced: share class and shareholder must belong to the organization; currency must
match the class; shares cannot be issued below par value; class authorized-share limit; treasury
classes cannot be issued to holders. Certificate numbers (`PREFIX-000001`) are allocated under a row
lock on the organization, so concurrent issuances cannot collide or overshoot the authorized limit.

## Cap table rules

* **Issued** = `ISSUED` rows of instrument type `SHARES`, excluding treasury.
* **Fully diluted** = issued + outstanding options, warrants and convertibles (1:1 into their class).
* **Voting power** = issued shares × class `voting_multiplier`. Options and treasury carry no votes.
* Percentages are 0–100 with 4 decimals; treasury shares are reported separately.

## Exit waterfall rules

* Classes with a `liquidation_multiple` are paid `multiple x invested capital` (quantity x issue price on the
  register), most senior `seniority_rank` first; equal ranks share pro rata when proceeds run short.
* The remainder is split by shares across common, participating preferred, and non-participating preferred
  that converts. Non-participating preferred converts when its as-converted payout beats its preference.
* Only issued shares count; options, warrants, convertibles and treasury are ignored. Amounts are rounded to
  2 decimals, so totals can differ from the exit value by a cent or two.

## Board resolutions and voting

* Lifecycle: `DRAFT` → `ACTIVE` (voting) → `PASSED` or `REJECTED`. Opening freezes total voting power as
  the quorum denominator; a vote's weight is the voter's voting power on the cap table when they vote.
* Votes are final (one per shareholder per resolution) and rejected after the deadline.
* Quorum: share of total voting power taking part (for, against or abstain), default 50%.
* Majority is measured on votes cast FOR and AGAINST only. `ORDINARY` needs more than 50%; `SPECIAL`
  needs at least 75%. Check these against your Articles of Association and the governing law for the
  entity, since thresholds differ by jurisdiction and resolution type.
* A corporate secretary can record a vote for a shareholder by passing `shareholderId`.

## Beneficial ownership (UBO)

Corporate shareholders can have recorded owners (`PUT /organizations/{id}/shareholders/{holderId}/owners`, percentages of the company's capital and votes; totals over 100% and circular ownership are rejected). `GET /organizations/{id}/ubo?threshold=25` looks through those links from each register holder's fully diluted economic share and weighted voting share, multiplying along each path and summing across paths. A natural person is a beneficial owner at or above the threshold on either measure (default 25%, inclusive; this reading of the UAE rule should be confirmed by counsel). Corporate holders with no recorded owners, or owners that add up to less than 100%, are reported as gaps rather than ignored. `GET /ubo/export?format=csv|pdf` downloads the schedule (CSV is UTF-8 with BOM and neutralises spreadsheet formulas) and each export is audited. The official MoE/MoC filing formats are not reproduced and the output says so. `scripts/ubo-smoke-test.py` runs a two-level holding structure end to end.

## Succession rules

A rule is a pre-agreed conversion: on a trigger event (founder death or incapacity, exit, transfer to an heir, IPO, custom), shares of one class convert to another class at a ratio, optionally for one named holder and optionally into a beneficiary's name, with a mandatory legal reference (e.g. the article of association). `GET .../succession-rules/{id}/preview` shows exactly what would change; `POST .../execute` (founder or secretary, MFA-verified, optionally naming a PASSED board resolution) retires each matching certificate (`CONVERTED`, or `TRANSFERRED` when the holder changes) and issues a new certificate that links back to its predecessor, with the price per share re-spread so the holder's total consideration is unchanged. Fractional shares, target-class authorised-share overruns and double execution are refused, and everything is audited. Applies to ordinary shares only; options, warrants and convertibles are not converted.

## Document vault

`POST .../vault` (multipart) stores AoA, notary receipts, agreements and similar as PDF, PNG, JPEG, DOCX or text up to 10 MB. The type is decided from the file's own bytes, the stored file name always gets the matching extension, a SHA-256 is recorded and re-checked on every download, downloads are `attachment` with `nosniff` and a sandbox CSP, and uploads are audited. There is no delete.

## Integrations: API keys and webhooks

Founders manage both under an organization. Neither can bypass MFA-protected writes.

**API keys** (`/organizations/{orgId}/api-keys`). A key looks like `eqk_<8>_<40>`, is shown once, and only its SHA-256 is stored.
Send it as `X-API-Key`. It acts as a legal auditor of that one organization and can only issue `GET` and `HEAD` requests;
anything else is refused with 403. Up to 10 active keys per organization; revoke at any time.

**Webhooks** (`/organizations/{orgId}/webhooks`, `GET /webhook-events` lists the catalogue). Events: `certificate.issued`,
`transfer.recorded`, `buyback.recorded`, `resolution.opened`, `resolution.closed`, plus `ping` for the Test button. Each
delivery is a JSON envelope `{id, type, createdAt, organizationId, data}` sent by `POST` with:

- `X-EquiGCC-Event` and `X-EquiGCC-Delivery` headers
- `X-EquiGCC-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of "<t>.<raw body>">`, keyed with the endpoint's `whsec_` secret
  (shown once). Verify the signature and reject timestamps older than a few minutes.

Events are written to a queue in the same transaction as the change, then sent by a scheduler. A non-2xx answer or
timeout is retried after 1 min, 5 min, 30 min, 2 h and 12 h, then marked failed; failed deliveries can be retried by hand.
Targets must be `https` and resolve to public addresses (checked again at send time; redirects are not followed).

| Setting | Default | Meaning |
|---|---|---|
| `WEBHOOKS_ENABLED` | `true` | Turns queueing and sending off |
| `WEBHOOKS_POLL_MS` | `15000` | How often the queue is polled |
| `WEBHOOKS_ALLOW_INSECURE` | `false` | Local testing only: allows `http://` and private addresses |

`scripts/integrations-smoke-test.py` exercises both against a running app (start it with the two testing settings above).

## Audit trail

Issuances, resolution changes and votes append to `audit_log` in the same transaction as the change.
Each entry's SHA-256 covers the previous entry's hash, a database trigger rejects `UPDATE` and `DELETE`,
and `GET …/audit` recomputes the chain and reports the first broken entry. This is a custom hash chain,
not Hibernate Envers, so it records business events rather than row-level diffs.

## JWT claims

```json
{ "sub": "user@firm.ae", "roles": ["FOUNDER"], "org_ids": ["<uuid>"], "amr": ["pwd", "otp"], "shareholder_id": "<uuid, optional>" }
```

Access tokens also carry `typ: access`; the short-lived token between password and TOTP steps has `typ: mfa_pending` and is rejected everywhere except `/auth/mfa/verify`. Data endpoints require `amr` to contain `otp`.

## Accounts, login and TOTP

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/auth/signup` | Creates an organization and its first FOUNDER, returns a password-only token and `mfaEnrollmentRequired` |
| `POST /api/v1/auth/mfa/enroll` | Returns a TOTP seed and `otpauth://` URI (render as QR) |
| `POST /api/v1/auth/mfa/confirm` | Confirms a code, enables TOTP, returns an MFA-verified token |
| `POST /api/v1/auth/login` | Password check; `OK`, `MFA_REQUIRED` (5-minute pending token) or `ORG_SELECTION_REQUIRED` |
| `POST /api/v1/auth/mfa/verify` | Pending token + code, returns the verified token |
| `POST /api/v1/auth/password/forgot` | Emails a reset link; always answers 202 so it cannot reveal which addresses have accounts; at most 3 per hour per account |
| `POST /api/v1/auth/password/reset` | Token plus new password; the link works once, expires in 30 minutes, only the newest link is valid, a lockout is cleared and 2FA stays on |
| `POST /api/v1/auth/password/change` | Signed-in user changes their password with the current one |
| `GET /api/v1/auth/me` | Current subject, org, role |
| `POST` / `GET /organizations/{id}/members` | FOUNDER invites (temporary password shown once) and lists members |
| `GET /organizations/{id}/certificates`, `/certificates/{no}/pdf` | List certificates and download a bilingual PDF with a verification QR (shareholders can download only their own) |
| `POST /organizations/{id}/share-classes`, `/shareholders` | FOUNDER or secretary creates register setup data (audited) |

TOTP is RFC 6238 (SHA-1, 6 digits, 30 s, plus or minus one step) with replay protection; secrets are AES-256-GCM encrypted at rest; passwords use BCrypt cost 12; five failed logins lock the account for 15 minutes; login errors are uniform. The `prod` profile refuses to start with the built-in development `JWT_SECRET` or `ENCRYPTION_KEY`. `scripts/auth-smoke-test.py` runs the whole flow against a live app.

## Run locally: full flow

Prerequisites: Git, and either Docker (option A) or Java 21, Maven, Node 20+ and a PostgreSQL 16 (option B).

### Option A: Docker (one command)

Check out both repos side by side, then start the whole stack:

```bash
git clone https://github.com/arjunsambyal997/Carta-New-Backend.git
git clone https://github.com/arjunsambyal997/Carta-New-Frontend.git
cd Carta-New-Backend
docker compose up --build
```

This starts PostgreSQL 16, the backend on http://localhost:8081 and the UI on http://localhost:3000. Flyway creates the schema on first start. Data is kept in the `pgdata` volume; `docker compose down -v` wipes it. Reset links are printed in the backend logs: `docker compose logs -f backend`.

### Option B: run the pieces yourself

```bash
# 1. Database. Use the container (host port 5433 here, in case a local Postgres already holds 5432),
#    or your own Postgres (see below)
docker run -d -p 5433:5432 -e POSTGRES_USER=equigcc -e POSTGRES_PASSWORD=equigcc -e POSTGRES_DB=equigcc postgres:16
export DB_URL=jdbc:postgresql://localhost:5433/equigcc     # PowerShell: $env:DB_URL="jdbc:postgresql://localhost:5433/equigcc"

# 2. Backend (terminal 1), listens on :8080. If that port is taken, set SERVER_PORT (any free port, e.g. 8081):
#    bash: SERVER_PORT=8081 mvn spring-boot:run     PowerShell: $env:SERVER_PORT="8081"; mvn spring-boot:run
mvn spring-boot:run

# 3. Frontend (terminal 2), listens on :3000
cd ../Carta-New-Frontend && npm install
BACKEND_URL=http://localhost:8080 npm run dev     # PowerShell: $env:BACKEND_URL="http://localhost:8080"; npm run dev
#   (use the same port you gave the backend, e.g. http://localhost:8081)
```

Using your own PostgreSQL instead of the container, for example database `postgres`, user `postgres`:

```bash
# macOS / Linux / Git Bash
DB_URL=jdbc:postgresql://localhost:5432/postgres DB_USER=postgres DB_PASSWORD=<your password> mvn spring-boot:run
```

```powershell
# Windows PowerShell (the VAR=value prefix form does not work there)
$env:DB_URL="jdbc:postgresql://localhost:5432/postgres"; $env:DB_USER="postgres"; $env:DB_PASSWORD="<your password>"; mvn spring-boot:run
```

Environment variables set with `$env:` last for that PowerShell window only. The development defaults for `JWT_SECRET` and `ENCRYPTION_KEY` are built in and fine locally. The `prod` profile refuses to start with them.

### Walk through the product

1. Open http://localhost:3000/signup. Create an organization (name, licence number, certificate prefix, which must be unique per organization). You become the FOUNDER.
2. `/security`: scan the QR with an authenticator app and enter a code. Data stays locked until two-factor is on.
3. `/setup`: add a share class and shareholders. `/members` invites team members (a temporary password is shown once).
4. `/certificates/new`: issue a certificate. `/certificates` lists them with English and Arabic PDFs. Scan the QR or open `/verify/<number>?h=<hash>` to check it.
5. `/` shows the cap table, `/ownership` the ownership tree, `/waterfall` an exit waterfall.
6. `/resolutions`: draft a board resolution, open voting, vote, close.
7. `/ubo`: record the owners of a corporate holder, review the beneficial owners, export CSV or PDF.
8. `/succession`: define a conversion rule, preview it, execute it. `/vault`: upload and download documents.
9. `/audit`: check the hash-chain integrity.
10. Password reset: sign out, choose "Forgot your password?", and enter your email. With no mail server set, the link is written to the backend log on a line starting `MAIL NOT CONFIGURED`. Open it, set a new password (12 characters or more), and sign in with the new password plus a code from your authenticator app. `/password` changes it while signed in.
11. The EN / العربية toggle in the top bar flips the language and the layout direction.

### Demo data

#### A full demo world on an empty database (recommended)

`scripts/seed-companies.mjs` loads five fictional companies with their history from incorporation to today, through the API (so certificate hashes, the audit chain and the analytics views come from the real code). It takes 30 to 100 seconds.

| Company | Jurisdiction | What it shows |
|---|---|---|
| Qamar Logistics Holdings Ltd (QLH) | ADGM, USD | Founders 2019, angels, seed 2021, Series A 2023, ESOP grants with vesting and exercises, a SAFE and a venture-debt warrant, secondary transfer, leaver buy-back, founder holding company, angel SPV, two-person approvals (pending, approved, rejected), signatures, API key |
| Sahara Health Labs Limited (SHL) | DIFC, USD | Seed-stage health tech, SPV in-specie distribution, family-office UBO chain, a DIFC beneficial-owner filing |
| Al Waha General Trading LLC (AWT) | Dubai mainland, AED | Family LLC since 1998: AED 1,000 shares, notarised gifts to children, family holding, non-voting profit shares, death and incapacity succession rules, partners-register and UBO filings |
| Rimal Financial Technology Company (RFT) | KSA, SAR | Closed JSC whose opening register is loaded from CSV (the import tool), Series A 2024, register filing, pending approval |
| Bayt Coffee Roasters LLC (BCR) | Dubai mainland, AED | The Qamar founder's second company (company switcher) |

Each company has resolutions in every state (passed, rejected, open with partial votes, draft), vault documents and logos.

**Demo logins.** Every account uses the password `Sijill@Demo2026` (change it with `$env:SEED_PASSWORD` before seeding).

| Email | Name | Role and company |
|---|---|---|
| omar@qamar.test | Omar Farouk | Founder, Qamar Logistics **and** Bayt Coffee |
| hessa@tamkeen.test | Hessa Al Marzooqi | Corporate secretary, Qamar Logistics **and** Al Waha |
| james@falconridge.test | James Whitfield | Board member, Qamar Logistics **and** Rimal |
| mona@qamar.test | Mona Al Zaabi | Board member, Qamar Logistics |
| elena@crescentaudit.test | Elena Petrova | Legal auditor, Qamar Logistics **and** Rimal |
| priya@qamar.test | Priya Raman | Shareholder portal, Qamar Logistics (co-founder) |
| tariq@nasser.test | Tariq Nasser | Shareholder portal, Qamar Logistics **and** Sahara Health (angel) |
| nadia@saharahealth.test | Dr. Nadia Al Hammadi | Founder, Sahara Health |
| farah@saharahealth.test | Farah Siddiqui | Corporate secretary, Sahara Health |
| khalid@saharahealth.test | Khalid Al Rumaithi | Board member, Sahara Health |
| saeed@alwaha.test | Saeed Rashid Al Falasi | Founder, Al Waha |
| maryam@alwaha.test | Maryam Al Falasi | Board member, Al Waha |
| hamad@alwaha.test | Hamad Al Falasi | Shareholder portal, Al Waha |
| faisal@rimal.test | Faisal Al Otaibi | Founder, Rimal |
| abeer@rimal.test | Abeer Al Qahtani | Corporate secretary, Rimal |
| sultan@rimal.test | Sultan Al Harbi | Board member, Rimal |
| lama@rimal.test | Lama Al Shehri | Shareholder portal, Rimal |
| lina@baytcoffee.test | Lina Saab | Shareholder portal, Bayt Coffee |

People in two companies see a company picker after signing in. Every account has two-factor enrolled. Start the backend with `DEV_SKIP_MFA=true` (development only) and email + password is enough; otherwise the authenticator secrets are written to `scripts/.seed-credentials.txt` (git-ignored) and `node scripts/totp.mjs <secret>` prints the current code.

What is not filled: ADGM has no filing deadlines modelled, so Qamar's Filing deadlines page is empty by design; the Analytics page needs Metabase set up (`docs/ANALYTICS.md`), and then reads the seeded rows from the `analytics.*` views. All names, licence numbers, funds and `.test` email domains are made up.

**Clean load on Windows PowerShell**, from the backend folder. Pick the block that matches how you run the database.

Docker (`docker compose`):

```powershell
git pull
docker compose down -v                                   # deletes the database volume: every company and user is gone
$env:DEV_SKIP_MFA="true"; $env:RATE_LIMIT_ENABLED="false"
docker compose up -d --build
# wait until this prints {"status":...}, then seed
curl.exe -s http://localhost:8081/api/v1/system/status
$env:API_URL="http://localhost:8081"; node scripts/seed-companies.mjs
```

Your own PostgreSQL (`mvn spring-boot:run`), using a dedicated database called `sijill`:

```powershell
git pull
# 1. wipe: drop and recreate the database (stop the backend first). psql is in C:\Program Files\PostgreSQL\16\bin if it is not on PATH
psql -U postgres -c "DROP DATABASE IF EXISTS sijill WITH (FORCE);" -c "CREATE DATABASE sijill;"

# 2. start the backend (terminal 1); Flyway creates the schema
$env:DB_URL="jdbc:postgresql://localhost:5432/sijill"; $env:DB_USER="postgres"; $env:DB_PASSWORD="<your password>"
$env:DEV_SKIP_MFA="true"; $env:RATE_LIMIT_ENABLED="false"
mvn spring-boot:run

# 3. seed (terminal 2, once the backend logs "Started")
$env:API_URL="http://localhost:8080"; node scripts/seed-companies.mjs
```

If you keep the data in the default `postgres` database instead, wipe it with
`psql -U postgres -d postgres -c "DROP SCHEMA IF EXISTS analytics CASCADE; DROP SCHEMA public CASCADE; CREATE SCHEMA public;"` and keep your usual `DB_URL`.

Running the seed twice is safe: a company whose founder already exists is skipped. With the rate limit left on, the seed pauses for a minute when it hits the sign-in limit and carries on.

#### Filling a company you created yourself

`scripts/seed-demo.mjs` (Node 20+, no install step) fills an organization you already created with realistic data: five share classes (voting, 10-vote founder, Series A preferred with a 1.5x preference, non-voting, treasury), 14 shareholders with Arabic names, issuances, holding companies with multi-level ownership for the UBO report (two beneficial owners, one three levels deep, plus one deliberate gap), five board resolutions (passed, rejected, open with partial votes, draft), three draft succession rules and six vault documents.

It asks for your email, password (hidden) and the current authenticator code; nothing is saved. In PowerShell, from the backend folder, with the backend on port 8081 as in the Docker setup (use 8080 for `mvn spring-boot:run`):

```powershell
$env:API_URL="http://localhost:8081"; node scripts/seed-demo.mjs
```

To skip the prompts: `$env:SEED_EMAIL`, `$env:SEED_PASSWORD` and `$env:SEED_TOTP`. Run it as the FOUNDER of the organization. It can be run again: anything that already exists is skipped. Board votes need a corporate secretary with two-factor on, so the script adds one throwaway secretary account (`demo-secretary-<random>@example.com`) whose password and two-factor secret are never shown or stored. Succession rules are left as drafts so you can preview and execute them yourself.

### Tests

```bash
mvn test                                     # unit + web-slice tests
./scripts/smoke-test.sh                      # end-to-end against the running app (needs psql)
python3 scripts/auth-smoke-test.py           # signup, login, TOTP, invites
python3 scripts/password-reset-smoke-test.py # reads the link from /tmp/backend.log, so run the backend with: mvn spring-boot:run > /tmp/backend.log
```

Other scripts in `scripts/`: `ubo-smoke-test.py`, `succession-vault-smoke-test.py`, `governance-smoke-test.sh`.

Password reset email goes out through SMTP when `spring.mail.host` (env `SPRING_MAIL_HOST`, plus `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`) is set, from `MAIL_FROM`, with links built from `PUBLIC_BASE_URL`. Without a host the link is written to the log (development only) and the `prod` profile refuses to start. A reset signs the account out everywhere. `scripts/password-reset-smoke-test.py` runs the flow.

Email notices use the same mail settings. A vote opening goes to every member and to every shareholder with an email address; a reminder goes once, in the 24 hours before the deadline, to people who have not voted yet; a certificate issued (or received through a transfer) is emailed to the holder with a verification link. Set `NOTICES_ENABLED=false` to turn them off. Without a mail host the notices are logged as `NOTICE (mail not configured)`. Imports never send email. `scripts/rounds-options-smoke-test.py` covers the round simulator, vesting, exercise and the vote-open notice.

Sign-in sessions live on the server. `POST /api/v1/auth/refresh` swaps a refresh token for a new access token and a new refresh token; using an old refresh token again signs the whole session out. `POST /api/v1/auth/logout` ends the session at once, and changing a password ends the account's other sessions. Sign-in, sign-up, code, password and refresh calls are limited per client (30 a minute by default).

Shareholder portal: invite a person with the role `SHAREHOLDER` linked to their holder record (Members page). `GET /api/v1/portal/summary` returns only their own holdings, certificates and open votes; they can vote, download their own certificates and sign them. Staff and shareholders sign resolutions and certificates with `POST /api/v1/organizations/{orgId}/signatures`, which needs a fresh authenticator code (a code already used to sign in cannot be used again, so wait for the next one). A signature records the signer, a SHA-256 of the document and the time; it shows as no longer valid if the document changes. It is an internal attestation, not a qualified e-signature under UAE or KSA law. `scripts/portal-signing-sessions-smoke-test.py` runs all of this against a live app (about 90 seconds).

Deploying on a server (Oracle VM + Cloudflare, blue-green releases, encrypted off-site backups): see `docs/DEPLOYMENT.md`. Shipping a release, telling clients ahead of a maintenance window, rolling back and restoring: `docs/RELEASE_RUNBOOK.md`.

Env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY` (base64, 32 bytes), `ACCESS_TOKEN_TTL` (default 15 minutes), `REFRESH_TOKEN_TTL` (14 days), `CERT_VERIFY_URL`, `NOTICES_ENABLED`, `RATE_LIMIT_*` (see `docs/DEPLOYMENT.md`).

## Not built yet (from the PRD)

Shared (Redis) rate limiting, qualified e-signature integration, official UBO filing formats.
