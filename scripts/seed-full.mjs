#!/usr/bin/env node
/**
 * The full tour. Everything seed-demo.mjs creates, plus a team that works in the same company so the founder can see what each
 * role does, and activity in every corner of the product:
 *
 *   - team accounts (one per role): corporate secretary, two board members, a legal auditor and two shareholder-portal logins,
 *     all with two-factor enrolled so they can vote and sign;
 *   - options with a vesting schedule and a partial exercise, a share transfer and a buy-back, a pending two-person approval
 *     waiting for the founder, an API key, a company logo, signatures, and a second company under the same login.
 *
 *   node scripts/seed-full.mjs
 *
 * Needs Node 20+. Environment: SEED_EMAIL, SEED_PASSWORD, SEED_TOTP (same as seed-demo.mjs), API_URL, SEED_ORG_ID,
 * SEED_DOMAIN (email domain for the team, default demo.sijill.example).
 *
 * The team's credentials are printed once at the end and written to scripts/.seed-credentials.txt (git-ignored): email, one-time
 * password, authenticator secret. `node scripts/totp.mjs <secret>` prints a current code for any of them. Demo data only; do not run
 * against a real company.
 *
 * Re-runnable: existing team members, classes, holders and documents are matched and skipped.
 */
import fs from "node:fs";
import { call, ok, log, totp, signInFounder, demoLogoPng } from "./seed-lib.mjs";
import { seedBase } from "./seed-base.mjs";

const DOMAIN = process.env.SEED_DOMAIN || "demo.sijill.example";
const ctx = await signInFounder();
const { token, org, P } = ctx;
log(`Signed in. Seeding organization ${org} with the full tour.`);

// ---- the team ---------------------------------------------------------------------------------------

const team = [
  { key: "secretary", email: `noura.secretary@${DOMAIN}`, name: "Noura Al Qasimi", role: "CORPORATE_SECRETARY" },
  { key: "board1", email: `hamad.board@${DOMAIN}`, name: "Hamad Al Ketbi", role: "BOARD_MEMBER" },
  { key: "board2", email: `reem.board@${DOMAIN}`, name: "Reem Bakr", role: "BOARD_MEMBER" },
  { key: "auditor", email: `daniel.auditor@${DOMAIN}`, name: "Daniel Ashworth", role: "LEGAL_AUDITOR" },
  { key: "layla", email: `layla.holder@${DOMAIN}`, name: "Layla Haddad", role: "SHAREHOLDER", holder: "Layla Haddad" },
  { key: "aisha", email: `aisha.holder@${DOMAIN}`, name: "Aisha Khan", role: "SHAREHOLDER", holder: "Aisha Khan" },
];

/** Signs in as a team member and enrolls two-factor; returns a verified token and the authenticator secret. */
async function onboard(m, temporaryPassword, holders) {
  const cred = { ...m };
  if (m.holder) cred.shareholderId = holders[m.holder].id;
  const sl = await ok("POST", "/auth/login", { body: { email: m.email, password: temporaryPassword, organizationId: org } });
  const enr = await ok("POST", "/auth/mfa/enroll", { token: sl.accessToken });
  const conf = await ok("POST", "/auth/mfa/confirm", { token: sl.accessToken, body: { code: totp(enr.secret) } });
  return { ...cred, password: temporaryPassword, secret: enr.secret, token: conf.accessToken };
}

// The base register comes first because shareholder-portal logins link to its holders. Votes are recorded by the real secretary,
// so she has to exist before the base data: create the holders she needs afterwards, then run the base seed once she is ready.
const existingMembers = new Map((await ok("GET", `${P}/members`, { token })).map((m) => [m.email, m]));
const accounts = {};
const pending = [];
for (const m of team.filter((t) => t.role !== "SHAREHOLDER")) {
  if (existingMembers.has(m.email)) { log(`  team member exists: ${m.email} (sign in with its saved credentials)`); continue; }
  const inv = await ok("POST", `${P}/members`, { token, body: { email: m.email, displayName: m.name, role: m.role } });
  accounts[m.key] = await onboard(m, inv.temporaryPassword, {});
  log(`  team member added: ${m.name} (${m.role})`);
}

const base = await seedBase(ctx, { secretaryToken: accounts.secretary?.token });
const { holders, classes } = base;

for (const m of team.filter((t) => t.role === "SHAREHOLDER")) {
  if (existingMembers.has(m.email)) { log(`  team member exists: ${m.email}`); continue; }
  const inv = await ok("POST", `${P}/members`, { token, body: { email: m.email, displayName: m.name, role: m.role, shareholderId: holders[m.holder].id } });
  accounts[m.key] = await onboard(m, inv.temporaryPassword, holders);
  log(`  shareholder login added: ${m.name} (holder ${m.holder})`);
}

// ---- options with vesting, then a partial exercise ------------------------------------------------------

const today = new Date();
const iso = (d) => d.toISOString().slice(0, 10);
const yearsAgo = (y) => iso(new Date(today.getFullYear() - y, today.getMonth(), 1));
const have = await ok("GET", `${P}/vesting`, { token });
const toured = have.length > 0; // option grants exist only after a first full run
if (!toured) {
  const grants = [["Aisha Khan", 48_000, 2], ["Yousef Al Hashimi", 36_000, 1], ["Mariam Al Suwaidi", 24_000, 3]];
  for (const [who, qty, yrs] of grants) {
    const c = await ok("POST", `${P}/certificates`, { token, body: { shareholderId: holders[who].id, shareClassId: classes["Ordinary Voting"].id,
      instrumentType: "OPTION", quantity: qty, pricePerShare: 1, currency: "AED", dateIssued: yearsAgo(yrs) } });
    await ok("PUT", `${P}/certificates/${c.certificateNo}/vesting`, { token, body: { startDate: yearsAgo(yrs), cliffMonths: 12, vestingMonths: 48 } });
    log(`  option grant: ${qty.toLocaleString("en-US")} for ${who}, vesting from ${yearsAgo(yrs)}`);
    if (who === "Aisha Khan") {
      await ok("POST", `${P}/certificates/${c.certificateNo}/exercise`, { token, body: { quantity: 8_000, date: iso(today) } });
      log("  exercised 8,000 of Aisha's vested options");
    }
  }
}

if (!toured) {
  // ---- a transfer and a buy-back (by the real secretary, so the audit trail shows who did what) ------------

  const actor = accounts.secretary?.token || token;
  const certs = await ok("GET", `${P}/certificates`, { token });
  if (!certs.some((c) => /secondary sale/i.test(c.reference || ""))) {
    await call("POST", `${P}/transfers`, { token: actor, body: { fromShareholderId: holders["Khalid Al Mansoori"].id, toShareholderId: holders["Rashid Al Dhaheri"].id,
      shareClassId: classes["Ordinary Voting"].id, quantity: 50_000, pricePerShare: 3.5, date: iso(today), reference: "Secondary sale, SPA-2026-014" } });
    log("  transfer recorded: Khalid -> Rashid, 50,000 Ordinary Voting");
  }
  if (!certs.some((c) => /buy-back offer/i.test(c.reference || ""))) {
    const bb = await call("POST", `${P}/buybacks`, { token: actor, body: { fromShareholderId: holders["Sara Thompson"].id,
      shareClassId: classes["Non-Voting Economic"].id, quantity: 10_000, pricePerShare: 2.5, date: iso(today), reference: "Buy-back offer 2026-Q4" } });
    log(bb.status === 201 ? "  buy-back recorded: 10,000 Non-Voting Economic from Sara Thompson" : `  buy-back skipped (${bb.status})`);
  }

  // ---- two-person approval: turn it on, then leave one change waiting for the founder -----------------------

  await ok("PUT", `${P}/settings/approvals`, { token, body: { required: true } });
  const waiting = await call("POST", `${P}/transfers`, { token: actor, body: { fromShareholderId: holders["Layla Haddad"].id, toShareholderId: holders["Mariam Al Suwaidi"].id,
    shareClassId: classes["Ordinary Voting"].id, quantity: 20_000, pricePerShare: 4, date: iso(today), reference: "Waiting for founder approval" } });
  log(waiting.status === 202 ? "  approvals on: a transfer from the secretary is waiting for you to approve it" : `  approvals on (transfer answered ${waiting.status})`);

  // ---- members act: holders vote, signatures -------------------------------------------------------------

  const list = await ok("GET", `${P}/resolutions`, { token });
  const open = list.find((r) => r.status === "ACTIVE" || r.status === "OPEN");
  if (open && accounts.layla) {
    const v = await call("POST", `${P}/resolutions/${open.id}/votes`, { token: accounts.layla.token, body: { vote: "FOR" } });
    log(`  Layla voted on "${open.title}" (${v.status})`);
  }
  if (open && accounts.aisha) {
    const v = await call("POST", `${P}/resolutions/${open.id}/votes`, { token: accounts.aisha.token, body: { vote: "ABSTAIN" } });
    log(`  Aisha voted on "${open.title}" (${v.status})`);
  }
  if (accounts.secretary) {
    const passed = list.find((r) => r.status === "PASSED");
    if (passed) {
      const s = await call("POST", `${P}/signatures`, { token: accounts.secretary.token, body: { documentType: "RESOLUTION", documentRef: passed.id, code: totp(accounts.secretary.secret, 1), statement: "Recorded as secretary" } });
      log(`  secretary signed "${passed.title}" (${s.status})`);
    }
  }
} else {
  log("  activity (transfers, approvals, votes, signatures) was added on an earlier run; skipping it so nothing is duplicated");
}

// ---- integrations and branding -------------------------------------------------------------------------

const keys = await ok("GET", `${P}/api-keys`, { token });
if (!keys.some((k) => k.name === "Reporting (read-only)")) {
  const k = await ok("POST", `${P}/api-keys`, { token, body: { name: "Reporting (read-only)" } });
  log(`  API key created (shown once): ${k.secret}`);
}
const form = new FormData();
form.append("file", new Blob([demoLogoPng()], { type: "image/png" }), "logo.png");
const lg = await call("PUT", `${P}/logo`, { token, form });
log(lg.status === 200 ? "  company logo uploaded" : `  logo upload skipped (${lg.status}); this backend may predate logos`);

// ---- a second company under the same login --------------------------------------------------------------

const orgs = await ok("GET", "/auth/organizations", { token });
if (!orgs.some((o) => /Desert Bloom/.test(o.name || o.legalNameEn || ""))) {
  const r = await call("POST", "/auth/organizations", { token, body: { legalNameEn: "Desert Bloom Foods LLC", legalNameAr: "ديزرت بلوم للأغذية ذ.م.م",
    jurisdiction: "MAINLAND_UAE", commercialLicenseNo: "CN-" + Math.floor(Math.random() * 9e6 + 1e6), baseCurrency: "AED", certificatePrefix: "DBF" } });
  log(r.status === 200 || r.status === 201 ? "  second company created: Desert Bloom Foods LLC (use the company switcher)" : `  second company skipped (${r.status})`);
}

// ---- credentials ----------------------------------------------------------------------------------------

const rows = Object.values(accounts);
if (rows.length) {
  const text = ["Sijill demo team (sign in at the app, then enter the authenticator code)", ""]
    .concat(rows.map((a) => `${a.role.padEnd(20)} ${a.email.padEnd(40)} password: ${a.password}   authenticator secret: ${a.secret}`))
    .concat(["", "Current code for any secret: node scripts/totp.mjs <secret>", "Change these passwords after the first sign-in."]).join("\n");
  fs.writeFileSync(new URL("./.seed-credentials.txt", import.meta.url), text + "\n", { mode: 0o600 });
  log("\n" + text);
} else {
  log("\nNo new team accounts were created; credentials from the first run are in scripts/.seed-credentials.txt.");
}
log("\nDone. Sign in as the founder and open Approvals, Resolutions, Options, Audit, Members and the company switcher.");
