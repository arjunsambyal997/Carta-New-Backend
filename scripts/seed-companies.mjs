#!/usr/bin/env node
/**
 * Loads a realistic demo world into an EMPTY database, through the API (so certificate hashes, the audit chain, notices and the
 * analytics views are all produced by the real code):
 *
 *   - 5 companies: Qamar Logistics (ADGM, venture-backed), Sahara Health Labs (DIFC, seed stage), Al Waha General Trading
 *     (Dubai mainland family LLC), Rimal Financial Technology (KSA closed JSC, register imported from CSV) and Bayt Coffee
 *     Roasters (Dubai mainland, the Qamar founder's second company);
 *   - 18 logins across every role, five of them in two companies;
 *   - registers that start at incorporation: founders, angels, seed and Series A rounds, ESOP grants with vesting and exercises,
 *     a SAFE and a warrant, secondary transfers, a leaver buy-back, holding-company chains for the UBO report, resolutions in
 *     every state with votes, two-person approvals (pending, approved, rejected), signatures, succession rules, vault documents,
 *     filing deadlines with past filings, CSV imports, logos and an API key.
 *
 *   node scripts/seed-companies.mjs
 *
 * Needs Node 20+ and the backend running. Environment:
 *   API_URL        backend base URL, default http://localhost:8080
 *   SEED_PASSWORD  password given to every demo login, default Sijill@Demo2026 (12+ characters)
 *
 * Every login gets two-factor enrolled. Run the backend with DEV_SKIP_MFA=true and you sign in with email and password only;
 * without it, the authenticator secrets are in scripts/.seed-credentials.txt (git-ignored) and `node scripts/totp.mjs <secret>`
 * prints a current code.
 *
 * Re-runnable: a company whose founder account already exists is skipped, so running it twice adds nothing. To start over,
 * wipe the database (see README, "Demo data").
 */
import fs from "node:fs";
import { call, ok as okOnce, log, totp, demoLogoPng } from "./seed-lib.mjs";
import { COMPANIES, PEOPLE } from "./seed-companies-data.mjs";

const PASSWORD = process.env.SEED_PASSWORD || "Sijill@Demo2026";
if (PASSWORD.length < 12) throw new Error("SEED_PASSWORD must be at least 12 characters");
const CRED_JSON = new URL("./.seed-credentials.json", import.meta.url);
const CRED_TXT = new URL("./.seed-credentials.txt", import.meta.url);

// ---- API calls that wait out the sign-in rate limit ------------------------------------------------------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// `opts` may be a function, so a retried call can take a fresh authenticator code.
async function ok(method, path, opts, expect) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await okOnce(method, path, typeof opts === "function" ? await opts() : opts, expect);
    } catch (e) {
      if (!/ -> 429:/.test(e.message) || attempt >= 6) throw e;
      if (attempt === 0) log("  (rate limited; waiting a minute. Start the backend with RATE_LIMIT_ENABLED=false to skip this)");
      await sleep(61_000);
    }
  }
}

// ---- sessions: one token per person and company, fresh authenticator codes --------------------------------

const saved = fs.existsSync(CRED_JSON) ? JSON.parse(fs.readFileSync(CRED_JSON, "utf8")) : {};
const secrets = new Map(Object.entries(saved.secrets || {}));
const lastStep = new Map();
const tokens = new Map();

/** A code for a time step this account has not used yet (the server rejects replays), waiting for the next step if needed. */
async function freshCode(email) {
  const secret = secrets.get(email);
  if (!secret) throw new Error(`No authenticator secret for ${email}. It was enrolled by an earlier run; wipe the database and run again.`);
  for (;;) {
    const now = Math.floor(Date.now() / 30000);
    const step = Math.max(now, (lastStep.get(email) ?? -1) + 1);
    if (step <= now + 1) {
      lastStep.set(email, step);
      return totp(secret, step - now);
    }
    await sleep(2_000);
  }
}

async function session(key, orgId) {
  const { email } = PEOPLE[key];
  const id = email + "|" + orgId;
  if (tokens.has(id)) return tokens.get(id);
  let r = await ok("POST", "/auth/login", { body: { email, password: PASSWORD, organizationId: orgId } });
  if (r.status === "MFA_REQUIRED") {
    const mfaToken = r.mfaToken;
    r = await ok("POST", "/auth/mfa/verify", async () => ({ body: { mfaToken, code: await freshCode(email) } }));
  }
  if (r.status !== "OK") throw new Error(`Could not sign in ${email}: ${r.status}`);
  tokens.set(id, r.accessToken);
  return r.accessToken;
}

/** Enrolls two-factor on a signed-in account and returns a verified token for orgId. */
async function enroll(email, token, orgId) {
  const enr = await ok("POST", "/auth/mfa/enroll", { token });
  secrets.set(email, enr.secret);
  const conf = await ok("POST", "/auth/mfa/confirm", async () => ({ token, body: { code: await freshCode(email) } }));
  tokens.set(email + "|" + orgId, conf.accessToken);
  return conf.accessToken;
}

function saveCredentials() {
  fs.writeFileSync(CRED_JSON, JSON.stringify({ secrets: Object.fromEntries(secrets) }, null, 2) + "\n", { mode: 0o600 });
}

// ---- small helpers ---------------------------------------------------------------------------------------

const daysFromNow = (d) => new Date(Date.now() + d * 86400000).toISOString();
const fmt = (n) => Number(n).toLocaleString("en-US");

function tinyPdf(title, lines) {
  const esc = (s) => s.replace(/[\\()]/g, "\\$&");
  const text = ["BT /F1 16 Tf 56 780 Td (" + esc(title) + ") Tj ET",
    ...lines.map((l, i) => `BT /F1 11 Tf 56 ${740 - i * 18} Td (${esc(l)}) Tj ET`),
    `BT /F1 9 Tf 56 60 Td (${esc("Demo document generated by scripts/seed-companies.mjs. Not a real record.")}) Tj ET`].join("\n");
  const objs = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    `<< /Length ${Buffer.byteLength(text)} >>\nstream\n${text}\nendstream`, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"];
  let out = "%PDF-1.4\n"; const offs = [];
  objs.forEach((o, i) => { offs.push(Buffer.byteLength(out)); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const x = Buffer.byteLength(out);
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n` + offs.map((o) => String(o).padStart(10, "0") + " 00000 n \n").join("")
    + `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${x}\n%%EOF\n`;
  return Buffer.from(out);
}

// ---- one company -----------------------------------------------------------------------------------------

const created = []; // [company, person, role]
const founderOrg = new Map();

async function createCompany(c) {
  const founder = PEOPLE[c.founder];
  let org, token;
  if (c.secondCompanyOf) {
    let first = founderOrg.get(c.founder);
    if (!first) {
      const r = await ok("POST", "/auth/login", { body: { email: founder.email, password: PASSWORD } });
      if (r.organizations?.some((o) => o.name === c.signup.legalNameEn)) return null;
      first = r.organizationId || r.organizations[0].id;
    }
    const t = await session(c.founder, first);
    if ((await ok("GET", "/auth/organizations", { token: t })).some((o) => o.name === c.signup.legalNameEn)) return null;
    const r = await ok("POST", "/auth/organizations", { token: t, body: c.signup });
    org = r.organizationId;
    token = r.accessToken;
    tokens.set(founder.email + "|" + org, token);
  } else {
    const r = await call("POST", "/auth/signup", { body: { email: founder.email, password: PASSWORD, displayName: founder.name, organization: c.signup } });
    if (r.status === 409 || r.status === 422 || r.status === 400) {
      if (/already exists/i.test(JSON.stringify(r.data))) return null;
      throw new Error(`signup for ${c.signup.legalNameEn} -> ${r.status}: ${JSON.stringify(r.data).slice(0, 300)}`);
    }
    if (r.status !== 200 && r.status !== 201) throw new Error(`signup for ${c.signup.legalNameEn} -> ${r.status}: ${JSON.stringify(r.data).slice(0, 300)}`);
    org = r.data.organizationId;
    token = await enroll(founder.email, r.data.accessToken, org);
    founderOrg.set(c.founder, org);
  }
  created.push([c.signup.legalNameEn, c.founder, "FOUNDER"]);
  return { org, token, P: `/organizations/${org}` };
}

async function addMember(c, ctx, m, holders) {
  const p = PEOPLE[m.person];
  const inv = await ok("POST", `${ctx.P}/members`, { token: ctx.token, body: { email: p.email, displayName: p.name, role: m.role,
    shareholderId: m.holder ? holders[m.holder].id : undefined } });
  if (inv.temporaryPassword) {
    // New account: sign in with the one-time password, set the shared demo password, enroll two-factor.
    const first = await ok("POST", "/auth/login", { body: { email: p.email, password: inv.temporaryPassword, organizationId: ctx.org } });
    await ok("POST", "/auth/password/change", { token: first.accessToken, body: { currentPassword: inv.temporaryPassword, newPassword: PASSWORD } });
    await enroll(p.email, first.accessToken, ctx.org);
  }
  created.push([c.signup.legalNameEn, m.person, m.role]);
  log(`  member: ${p.name} <${p.email}> as ${m.role}${inv.temporaryPassword ? "" : " (existing login, now in two companies)"}`);
}

async function seedCompany(c) {
  log(`\n== ${c.signup.legalNameEn} (${c.signup.jurisdiction}, ${c.signup.baseCurrency})`);
  const ctx = await createCompany(c);
  if (!ctx) { log("  already loaded; skipped"); return; }
  const { org, token, P } = ctx;
  const officers = c.members.filter((m) => m.role !== "SHAREHOLDER");
  const portal = c.members.filter((m) => m.role === "SHAREHOLDER");

  // Logo
  const form = new FormData();
  form.append("file", new Blob([demoLogoPng(128, c.colour)], { type: "image/png" }), "logo.png");
  await ok("PUT", `${P}/logo`, { token, form });

  // Team first: the secretary records votes and proposes changes later.
  for (const m of officers) await addMember(c, ctx, m, {});
  const secretary = officers.find((m) => m.role === "CORPORATE_SECRETARY")?.person;
  const actor = secretary ? await session(secretary, org) : token;

  // Share classes
  const classes = {};
  for (const d of c.classes) classes[d.name] = await ok("POST", `${P}/share-classes`, { token, body: { participating: false, ...d } });
  log(`  ${c.classes.length} share classes`);

  // Opening register from CSV (Rimal), the way a company moving off a spreadsheet starts.
  if (c.imports) {
    for (const [kind, rows] of [["shareholders", c.imports.holders], ["issuances", c.imports.issuances]]) {
      const f = new FormData();
      f.append("file", new Blob([rows.join("\n") + "\n"], { type: "text/csv" }), `${kind}.csv`);
      const r = await ok("POST", `${P}/import/${kind}?dryRun=false`, { token: actor, form: f });
      log(`  CSV import of ${kind}: ${JSON.stringify(r).slice(0, 120)}`);
    }
  }

  // Shareholders
  const holders = {};
  for (const h of await ok("GET", `${P}/shareholders`, { token })) holders[h.fullNameEn] = h;
  for (const h of c.holders) if (!holders[h.fullNameEn]) holders[h.fullNameEn] = await ok("POST", `${P}/shareholders`, { token, body: h });
  log(`  ${Object.keys(holders).length} shareholders`);

  // Issuances, in date order
  const certs = {};
  for (const [who, cls, quantity, price, date, instrumentType] of c.issuances) {
    const r = await ok("POST", `${P}/certificates`, { token: actor, body: { shareholderId: holders[who].id, shareClassId: classes[cls].id,
      instrumentType: instrumentType || "SHARES", quantity, pricePerShare: price, dateIssued: date } });
    (certs[who] ||= []).push(r.certificateNo);
  }
  log(`  ${c.issuances.length} issuances`);

  // ESOP grants with vesting, then exercises
  const optionCert = {};
  for (const [who, cls, quantity, strike, date, cliff, months] of c.options) {
    const r = await ok("POST", `${P}/certificates`, { token: actor, body: { shareholderId: holders[who].id, shareClassId: classes[cls].id,
      instrumentType: "OPTION", quantity, pricePerShare: strike, dateIssued: date } });
    await ok("PUT", `${P}/certificates/${r.certificateNo}/vesting`, { token: actor, body: { startDate: date, cliffMonths: cliff, vestingMonths: months } });
    optionCert[who] = r.certificateNo;
  }
  if (c.options.length) log(`  ${c.options.length} option grants with vesting`);
  for (const [who, quantity, date] of c.exercises) {
    await ok("POST", `${P}/certificates/${optionCert[who]}/exercise`, { token: actor, body: { quantity, date } });
    log(`  ${who} exercised ${fmt(quantity)} options on ${date}`);
  }

  // Transfers and buy-backs
  for (const [from, to, cls, quantity, price, date, reference] of c.transfers) {
    await ok("POST", `${P}/transfers`, { token: actor, body: { fromShareholderId: holders[from].id, toShareholderId: holders[to].id,
      shareClassId: classes[cls].id, quantity, pricePerShare: price, date, reference } });
    log(`  transfer ${date}: ${fmt(quantity)} ${cls} ${from} -> ${to}`);
  }
  for (const [from, cls, quantity, price, date, reference] of c.buybacks) {
    await ok("POST", `${P}/buybacks`, { token: actor, body: { fromShareholderId: holders[from].id, shareClassId: classes[cls].id,
      quantity, pricePerShare: price, date, reference } });
    log(`  buy-back ${date}: ${fmt(quantity)} ${cls} from ${from}`);
  }

  // Ownership chains for the UBO report
  for (const [owned, owners] of Object.entries(c.owners)) {
    await ok("PUT", `${P}/shareholders/${holders[owned].id}/owners`, { token, body: {
      owners: owners.map(([n, pct]) => ({ ownerId: holders[n].id, economicPct: pct })) } });
  }
  if (Object.keys(c.owners).length) log(`  ownership chains for ${Object.keys(c.owners).join(", ")}`);

  // Shareholder-portal logins (they link to holder records, so they come after the register)
  for (const m of portal) await addMember(c, ctx, m, holders);

  // Resolutions: votes on behalf of holders are recorded by the secretary; portal users vote themselves.
  const resolutions = {};
  for (const d of c.resolutions) {
    const r = await ok("POST", `${P}/resolutions`, { token, body: { title: d.title, titleAr: d.titleAr, description: d.description,
      threshold: d.threshold, quorumPct: 50, votingDeadline: daysFromNow(d.days || 21) } });
    resolutions[d.title] = r;
    if (d.end !== "DRAFT") await ok("POST", `${P}/resolutions/${r.id}/open`, { token });
    for (const [who, vote] of d.votes) {
      await ok("POST", `${P}/resolutions/${r.id}/votes`, { token: actor, body: { vote, shareholderId: holders[who].id } });
    }
    for (const [who, vote] of d.portalVotes || []) {
      await ok("POST", `${P}/resolutions/${r.id}/votes`, { token: await session(who, org), body: { vote } });
    }
    let status = d.end;
    if (d.end === "PASSED" || d.end === "REJECTED") {
      status = (await ok("POST", `${P}/resolutions/${r.id}/close`, { token })).status;
      if (status !== d.end) log(`  WARNING: "${d.title}" closed as ${status}, expected ${d.end}`);
    }
    log(`  resolution [${status}] ${d.title}`);
  }

  // Succession rules (drafts: preview and execute them from the UI)
  for (const d of c.succession) {
    await ok("POST", `${P}/succession-rules`, { token, body: { name: d.name, trigger: d.trigger, subjectId: d.subject && holders[d.subject].id,
      fromClassId: classes[d.from].id, toClassId: classes[d.to].id, ratioNumerator: d.num, ratioDenominator: d.den,
      beneficiaryId: d.beneficiary && holders[d.beneficiary].id, legalReference: d.legalReference } });
  }
  if (c.succession.length) log(`  ${c.succession.length} succession rules`);

  // Vault
  for (const [title, category, lines] of c.vault) {
    const f = new FormData();
    f.append("title", title);
    f.append("category", category);
    f.append("file", new Blob([tinyPdf(title, lines)], { type: "application/pdf" }), title.toLowerCase().replace(/[^a-z0-9]+/g, "-") + ".pdf");
    await ok("POST", `${P}/vault`, { token: actor, form: f });
  }
  log(`  ${c.vault.length} vault documents`);

  // Past filings against the deadlines this jurisdiction has
  for (const [key, filedOn, reference] of c.filings) {
    await ok("POST", `${P}/compliance/${key}/filings`, { token, body: { filedOn, reference } });
    log(`  filing recorded: ${key} on ${filedOn} (${reference})`);
  }

  // Signatures
  for (const s of c.signatures) {
    const ref = s.type === "RESOLUTION" ? resolutions[s.resolution].id : certs[s.holder][0];
    const p = PEOPLE[s.person];
    const signer = await session(s.person, org);
    await ok("POST", `${P}/signatures`, async () => ({ token: signer, body: { documentType: s.type, documentRef: String(ref),
      code: await freshCode(p.email), statement: s.statement } }));
    log(`  ${p.name} signed ${s.type === "RESOLUTION" ? `"${s.resolution}"` : `certificate ${ref}`}`);
  }

  // Two-person approval: switch it on, then the secretary proposes changes that board members decide (or leave pending).
  if (c.approvalsOn) {
    await ok("PUT", `${P}/settings/approvals`, { token, body: { required: true } });
    for (const a of c.approvals) {
      let path, body;
      if (a.kind === "issue") {
        path = `${P}/certificates`;
        body = { shareholderId: holders[a.holder].id, shareClassId: classes[a.cls].id, instrumentType: a.instrument, quantity: a.quantity, pricePerShare: a.price, dateIssued: a.date };
      } else if (a.kind === "transfer") {
        path = `${P}/transfers`;
        body = { fromShareholderId: holders[a.from].id, toShareholderId: holders[a.to].id, shareClassId: classes[a.cls].id, quantity: a.quantity, pricePerShare: a.price, date: a.date, reference: a.reference };
      } else {
        path = `${P}/certificates/${optionCert[a.holder]}/exercise`;
        body = { quantity: a.quantity, date: a.date };
      }
      const pending = await ok("POST", path, { token: actor, body }, [202]);
      if (a.decide) {
        const [who, verb, note] = a.decide;
        await ok("POST", `${P}/approvals/${pending.id}/${verb}`, { token: await session(who, org), body: { note } });
      }
      log(`  approval ${a.decide ? a.decide[1] + "d by " + PEOPLE[a.decide[0]].name : "waiting"}: ${pending.summary}`);
    }
  }

  if (c.apiKey) {
    await ok("POST", `${P}/api-keys`, { token, body: { name: c.apiKey } });
    log(`  API key "${c.apiKey}" (the secret is not kept; make a new one in Integrations if you need it)`);
  }
  saveCredentials();
}

// ---- run -------------------------------------------------------------------------------------------------

const t0 = Date.now();
for (const c of COMPANIES) await seedCompany(c);
saveCredentials();

if (created.length) {
  const byPerson = new Map();
  for (const [company, key, role] of created) {
    const e = byPerson.get(key) || { ...PEOPLE[key], roles: [] };
    e.roles.push(`${role} @ ${company}`);
    byPerson.set(key, e);
  }
  const lines = ["Sijill demo logins (all use the same password)", `Password: ${PASSWORD}`, ""];
  for (const e of byPerson.values()) {
    lines.push(`${e.email.padEnd(28)} ${e.name}`);
    for (const r of e.roles) lines.push(`${"".padEnd(28)}   ${r}`);
    lines.push(`${"".padEnd(28)}   authenticator secret: ${secrets.get(e.email)}`);
  }
  lines.push("", "Backend started with DEV_SKIP_MFA=true: email + password is enough.",
    "Otherwise: node scripts/totp.mjs <secret> prints the current 6-digit code.");
  fs.writeFileSync(CRED_TXT, lines.join("\n") + "\n", { mode: 0o600 });
  log("\n" + lines.join("\n"));
}
log(`\nDone in ${Math.round((Date.now() - t0) / 1000)}s.`);
