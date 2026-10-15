# EquiGCC Platform (backend scaffold)

Spring Boot 3.3 / Java 21 backend for the EquiGCC digital share register: multi-class cap table
and share certificate issuance for UAE (Mainland, ADGM, DIFC) and KSA entities.

## What's here

| Area | Files |
|---|---|
| Build | `pom.xml`: web, validation, data-jpa, security, flyway (+ PostgreSQL module), postgresql, jjwt 0.12, springdoc |
| Schema | `src/main/resources/db/migration/V1__init_share_register.sql` (Flyway; Hibernate runs in `validate` mode) |
| Entities | `domain/`: `Organization`, `ShareClass`, `Shareholder`, `ShareIssuance` + enums, `BaseEntity` (UUID id, `@Version`, audit timestamps) |
| Cap table | `service/CapTableService.java` → `CapTable` record |
| Issuance | `service/CertificateIssuanceService.java`, `service/CertificateHasher.java` (SHA-256) |
| API | `web/ShareCertificateController.java` |
| Security | `security/`: HS256 JWT filter, roles, per-organization tenant check, TOTP (`amr`) check |

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

## Run

```bash
docker run -d -p 5432:5432 -e POSTGRES_USER=equigcc -e POSTGRES_PASSWORD=equigcc -e POSTGRES_DB=equigcc postgres:16
JWT_SECRET=<32+ random bytes> mvn spring-boot:run
mvn test                          # unit + web-slice tests
./scripts/smoke-test.sh           # end-to-end against the running app (needs psql)
```

Password reset email goes out through SMTP when `spring.mail.host` (env `SPRING_MAIL_HOST`, plus `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`) is set, from `MAIL_FROM`, with links built from `PUBLIC_BASE_URL`. Without a host the link is written to the log (development only) and the `prod` profile refuses to start. Existing access tokens stay valid until they expire (one hour) after a reset. `scripts/password-reset-smoke-test.py` runs the flow.

Env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY` (base64, 32 bytes), `ACCESS_TOKEN_TTL`, `CERT_VERIFY_URL`.

## Not built yet (from the PRD)

Redis rate limiting, e-signature integration, official UBO filing formats.
