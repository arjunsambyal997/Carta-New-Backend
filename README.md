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

## JWT claims

```json
{ "sub": "user@firm.ae", "roles": ["FOUNDER"], "org_ids": ["<uuid>"], "amr": ["pwd", "otp"] }
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

PDF certificate rendering (bilingual + QR), Envers audit trail / `AuditLog`, `BoardResolution` and
`VoteRecord`, exit waterfall, succession rules, UBO exports, Redis rate limiting, TOTP enrollment.
