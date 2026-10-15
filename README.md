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

## Audit trail

Issuances, resolution changes and votes append to `audit_log` in the same transaction as the change.
Each entry's SHA-256 covers the previous entry's hash, a database trigger rejects `UPDATE` and `DELETE`,
and `GET …/audit` recomputes the chain and reports the first broken entry. This is a custom hash chain,
not Hibernate Envers, so it records business events rather than row-level diffs.

## JWT claims

```json
{ "sub": "user@firm.ae", "roles": ["FOUNDER"], "org_ids": ["<uuid>"], "amr": ["pwd", "otp"], "shareholder_id": "<uuid, optional>" }
```

`JwtService.issue(...)` mints tokens; the login + TOTP enrollment flow is not built yet.

## Run

```bash
docker run -d -p 5432:5432 -e POSTGRES_USER=equigcc -e POSTGRES_PASSWORD=equigcc -e POSTGRES_DB=equigcc postgres:16
JWT_SECRET=<32+ random bytes> mvn spring-boot:run
mvn test                          # unit + web-slice tests
./scripts/smoke-test.sh           # end-to-end against the running app (needs psql)
```

Env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CERT_VERIFY_URL`.

## Not built yet (from the PRD)

PDF certificate rendering (bilingual + QR), succession rules, UBO exports, Redis rate limiting, TOTP enrollment.
