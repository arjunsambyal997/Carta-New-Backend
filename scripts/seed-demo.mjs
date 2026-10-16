#!/usr/bin/env node
/**
 * Fills an existing EquiGCC organization with realistic demo data so every screen has something to show:
 * share classes, individual and corporate shareholders (English + Arabic), issuances, multi-level holding
 * chains for the UBO report, board resolutions in every state with votes cast, draft succession rules and
 * vault documents.
 *
 *   node scripts/seed-demo.mjs
 *
 * Needs Node 20+. Nothing is stored: the password and the authenticator code are read from the environment
 * or typed at a prompt (the password is not echoed).
 *
 *   SEED_EMAIL      founder's email (prompted when missing)
 *   SEED_PASSWORD   founder's password (prompted, hidden, when missing)
 *   SEED_TOTP       current 6-digit authenticator code (prompted when missing)
 *   API_URL         backend base URL, default http://localhost:8080
 *   SEED_ORG_ID     only when the account belongs to several organizations
 *
 * Re-runnable: classes, holders, resolutions, rules and documents are matched by name and skipped when they
 * already exist; ownership links are replaced with the same values. Issuances are skipped for any holder
 * that already holds the class.
 *
 * Board votes must be recorded by a corporate secretary with two-factor on, so the first run adds one
 * throwaway secretary account (demo-secretary-<random>@example.com) with a random password and a two-factor secret
 * that are never shown or saved. It can cast the demo votes and cannot be signed in to afterwards.
 */
import crypto from "node:crypto";
import readline from "node:readline";

const API = (process.env.API_URL || "http://localhost:8080").replace(/\/$/, "") + "/api/v1";

// ---- helpers -----------------------------------------------------------------------------------------

function ask(question, { hidden = false } = {}) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    if (hidden) {
      rl._writeToOutput = (s) => { if (s.includes(question)) process.stdout.write(s); };
    }
    rl.question(question, (answer) => { rl.close(); if (hidden) process.stdout.write("\n"); resolve(answer.trim()); });
  });
}

const base32 = (s) => {
  const a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of s.replace(/=+$/, "")) bits += a.indexOf(c).toString(2).padStart(5, "0");
  const out = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) out.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(out);
};
function totp(secret, offset = 0) {
  const b = Buffer.alloc(8);
  b.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000) + offset));
  const h = crypto.createHmac("sha1", base32(secret)).update(b).digest();
  const o = h[19] & 15;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1000000).padStart(6, "0");
}

async function call(method, path, { body, token, form, raw } = {}) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  let payload;
  if (form) payload = form;
  else if (body !== undefined) { headers["Content-Type"] = "application/json"; payload = JSON.stringify(body); }
  let res;
  try {
    res = await fetch(API + path, { method, headers, body: payload });
  } catch (e) {
    throw new Error(`Cannot reach ${API} (${e.cause?.code || e.message}). Is the backend running? Set API_URL if it is on another port.`);
  }
  const buf = Buffer.from(await res.arrayBuffer());
  if (raw) return { status: res.status, data: buf };
  let data = null;
  try { data = buf.length ? JSON.parse(buf.toString()) : null; } catch { data = buf.toString(); }
  return { status: res.status, data };
}

async function ok(method, path, opts, expect = [200, 201, 204]) {
  const r = await call(method, path, opts);
  if (!expect.includes(r.status)) {
    const msg = typeof r.data === "object" && r.data ? (r.data.message || r.data.detail || r.data.error || JSON.stringify(r.data)) : r.data;
    throw new Error(`${method} ${path} -> ${r.status}: ${String(msg).slice(0, 300)}`);
  }
  return r.data;
}

const log = (s) => console.log(s);

// ---- sign in -----------------------------------------------------------------------------------------

const email = process.env.SEED_EMAIL || (await ask("Founder email: "));
const password = process.env.SEED_PASSWORD || (await ask("Password: ", { hidden: true }));

let login = await ok("POST", "/auth/login", { body: { email, password, organizationId: process.env.SEED_ORG_ID || undefined } });
if (login.status === "CHOOSE_ORGANIZATION" || login.organizations?.length) {
  log("This account belongs to several organizations. Re-run with SEED_ORG_ID set to one of:");
  for (const o of login.organizations) log(`  ${o.id}  ${o.name} (${o.role})`);
  process.exit(1);
}
let token;
if (login.status === "MFA_REQUIRED") {
  const code = process.env.SEED_TOTP || (await ask("Authenticator code (6 digits): "));
  const v = await ok("POST", "/auth/mfa/verify", { body: { mfaToken: login.mfaToken, code } });
  token = v.accessToken;
} else {
  throw new Error("Two-factor is not set up on this account yet. Open /security in the app, enable it, then run this again.");
}
const me = await ok("GET", "/auth/me", { token });
if (me.role !== "FOUNDER") throw new Error(`Signed in as ${me.role}; the seed needs the FOUNDER account.`);
const org = me.organizationId;
const P = `/organizations/${org}`;
log(`Signed in. Seeding organization ${org}.`);

// ---- share classes -----------------------------------------------------------------------------------

const classDefs = [
  { name: "Ordinary Voting", nameAr: "أسهم عادية", classType: "COMMON_VOTING", parValue: 1, currency: "AED", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 10_000_000 },
  { name: "Founder Shares", nameAr: "أسهم المؤسسين", classType: "PREFERRED_FOUNDER", parValue: 1, currency: "AED", votingMultiplier: 10, seniorityRank: 2, authorizedShares: 1_000_000 },
  { name: "Series A Preferred", nameAr: "أسهم ممتازة - الفئة أ", classType: "PREFERRED_FOUNDER", parValue: 1, currency: "AED", votingMultiplier: 1, seniorityRank: 3, liquidationMultiple: 1.5, participating: false, authorizedShares: 2_000_000 },
  { name: "Non-Voting Economic", nameAr: "أسهم اقتصادية بدون تصويت", classType: "NON_VOTING_ECONOMIC", parValue: 1, currency: "AED", votingMultiplier: 0, seniorityRank: 1, authorizedShares: 1_000_000 },
  { name: "Treasury", nameAr: "أسهم الخزينة", classType: "TREASURY", parValue: 1, currency: "AED", votingMultiplier: 0, seniorityRank: 1, authorizedShares: 500_000 },
];
const classes = {};
for (const c of await ok("GET", `${P}/share-classes`, { token })) classes[c.name] = c;
for (const d of classDefs) {
  if (classes[d.name]) { log(`  class exists: ${d.name}`); continue; }
  classes[d.name] = await ok("POST", `${P}/share-classes`, { token, body: d });
  log(`  class created: ${d.name}`);
}

// ---- shareholders ------------------------------------------------------------------------------------

const holderDefs = [
  ["Fatima Al Noor", "فاطمة النور", "INDIVIDUAL", "AE"],
  ["Khalid Al Mansoori", "خالد المنصوري", "INDIVIDUAL", "AE"],
  ["Layla Haddad", "ليلى حداد", "INDIVIDUAL", "LB"],
  ["Omar Saleh", "عمر صالح", "INDIVIDUAL", "JO"],
  ["Aisha Khan", "عائشة خان", "INDIVIDUAL", "PK"],
  ["Yousef Al Hashimi", "يوسف الهاشمي", "INDIVIDUAL", "AE"],
  ["Mariam Al Suwaidi", "مريم السويدي", "INDIVIDUAL", "AE"],
  ["Rashid Al Dhaheri", "راشد الظاهري", "INDIVIDUAL", "AE"],
  ["Sara Thompson", null, "INDIVIDUAL", "GB"],
  ["Noor Holding LLC", "نور القابضة ذ.م.م", "CORPORATE", "AE", "RC-100234"],
  ["Gulf Ventures FZE", "الخليج للمشاريع م.ح.", "CORPORATE", "AE", "RC-204118"],
  ["Al Mansoori Family Office LLC", "المكتب العائلي للمنصوري ذ.م.م", "CORPORATE", "AE", "RC-310552"],
  ["Horizon Growth Fund LP", "صندوق هورايزن للنمو", "CORPORATE", "KY", "KY-88120"],
  ["Emirates Tech Investments PJSC", "الإمارات للاستثمارات التقنية ش.م.ع", "CORPORATE", "AE", "RC-415067"],
];
const holders = {};
for (const h of await ok("GET", `${P}/shareholders`, { token })) holders[h.fullNameEn] = h;
for (const [en, ar, type, nat, reg] of holderDefs) {
  if (holders[en]) continue;
  holders[en] = await ok("POST", `${P}/shareholders`, { token, body: {
    holderType: type, fullNameEn: en, fullNameAr: ar || undefined, nationality: nat, registrationNo: reg,
    email: type === "INDIVIDUAL" ? en.toLowerCase().replace(/[^a-z]+/g, ".") + "@example.com" : undefined } });
  log(`  holder created: ${en}`);
}

// ---- issuances ---------------------------------------------------------------------------------------

const issuances = [
  ["Noor Holding LLC", "Ordinary Voting", 4_000_000, 1],
  ["Fatima Al Noor", "Founder Shares", 100_000, 1],
  ["Khalid Al Mansoori", "Ordinary Voting", 500_000, 1],
  ["Layla Haddad", "Ordinary Voting", 250_000, 1.2],
  ["Layla Haddad", "Non-Voting Economic", 150_000, 1.2],
  ["Aisha Khan", "Ordinary Voting", 200_000, 1.5],
  ["Yousef Al Hashimi", "Ordinary Voting", 150_000, 1.5],
  ["Mariam Al Suwaidi", "Ordinary Voting", 100_000, 2],
  ["Sara Thompson", "Non-Voting Economic", 100_000, 2],
  ["Horizon Growth Fund LP", "Series A Preferred", 800_000, 5],
  ["Emirates Tech Investments PJSC", "Series A Preferred", 300_000, 5],
];
const existing = new Set((await ok("GET", `${P}/certificates`, { token })).map((c) => c.shareholderNameEn + "|" + c.shareClassName));
const today = new Date().toISOString().slice(0, 10);
for (const [who, cls, qty, price] of issuances) {
  if (existing.has(who + "|" + cls)) continue;
  await ok("POST", `${P}/certificates`, { token, body: {
    shareholderId: holders[who].id, shareClassId: classes[cls].id, quantity: qty, pricePerShare: price, currency: "AED", dateIssued: today } });
  log(`  issued ${qty.toLocaleString("en-US")} ${cls} to ${who}`);
}

// ---- ownership chains (UBO) --------------------------------------------------------------------------

const chains = {
  "Noor Holding LLC": [["Fatima Al Noor", 40], ["Gulf Ventures FZE", 60]],
  "Gulf Ventures FZE": [["Omar Saleh", 30], ["Al Mansoori Family Office LLC", 70]],
  "Al Mansoori Family Office LLC": [["Khalid Al Mansoori", 80], ["Mariam Al Suwaidi", 20]],
  "Horizon Growth Fund LP": [["Emirates Tech Investments PJSC", 55], ["Sara Thompson", 45]],
  // 75% recorded on purpose: the UBO report shows the unexplained 25% as a gap.
  "Emirates Tech Investments PJSC": [["Yousef Al Hashimi", 40], ["Rashid Al Dhaheri", 35]],
};
// Owners must be saved bottom-up only for readability; the API accepts any order as long as there is no cycle.
for (const [owned, owners] of Object.entries(chains)) {
  await ok("PUT", `${P}/shareholders/${holders[owned].id}/owners`, { token, body: {
    owners: owners.map(([n, pct]) => ({ ownerId: holders[n].id, economicPct: pct })) } });
  log(`  owners set for ${owned}`);
}

// ---- board resolutions -------------------------------------------------------------------------------

const resolutionDefs = [
  { title: "Approve the 2027 budget", titleAr: "اعتماد ميزانية 2027", description: "Annual operating budget of AED 12.4m as presented by management.",
    threshold: "ORDINARY", quorumPct: 50, end: "PASSED",
    votes: [["Noor Holding LLC", "FOR"], ["Fatima Al Noor", "FOR"], ["Khalid Al Mansoori", "FOR"], ["Horizon Growth Fund LP", "AGAINST"], ["Aisha Khan", "ABSTAIN"]] },
  { title: "Amend the articles: raise authorised capital", titleAr: "تعديل النظام الأساسي: زيادة رأس المال المصرح به", description: "Special resolution needing 75% of the votes cast.",
    threshold: "SPECIAL", quorumPct: 50, end: "REJECTED",
    votes: [["Noor Holding LLC", "FOR"], ["Khalid Al Mansoori", "FOR"], ["Fatima Al Noor", "AGAINST"], ["Horizon Growth Fund LP", "AGAINST"], ["Emirates Tech Investments PJSC", "AGAINST"]] },
  { title: "Authorise the founder succession plan", titleAr: "اعتماد خطة تعاقب المؤسسين", description: "Approves the draft succession rules held in the register.",
    threshold: "SPECIAL", quorumPct: 50, end: "PASSED",
    votes: [["Noor Holding LLC", "FOR"], ["Fatima Al Noor", "FOR"], ["Khalid Al Mansoori", "FOR"], ["Layla Haddad", "FOR"], ["Aisha Khan", "FOR"]] },
  { title: "Appoint the external auditor for FY2026", titleAr: "تعيين مدقق الحسابات الخارجي للسنة المالية 2026", description: "Voting is open and only some holders have voted.",
    threshold: "ORDINARY", quorumPct: 50, end: "OPEN",
    votes: [["Fatima Al Noor", "FOR"], ["Khalid Al Mansoori", "FOR"]] },
  { title: "Approve a related-party loan", titleAr: "الموافقة على قرض لطرف ذي صلة", description: "Draft, not yet opened for voting.",
    threshold: "ORDINARY", quorumPct: 50, end: "DRAFT", votes: [] },
];
const haveRes = new Map((await ok("GET", `${P}/resolutions`, { token })).map((r) => [r.title, r]));
const todo = resolutionDefs.filter((d) => !haveRes.has(d.title));
let secretaryToken = null;
if (todo.some((d) => d.votes.length)) {
  const secEmail = process.env.SEED_SECRETARY_EMAIL || `demo-secretary-${crypto.randomBytes(3).toString("hex")}@example.com`;
  const inv = await call("POST", `${P}/members`, { token, body: { email: secEmail, displayName: "Demo Board Secretary", role: "CORPORATE_SECRETARY" } });
  if (inv.status !== 201 || !inv.data.temporaryPassword) {
    throw new Error(`Could not create the demo secretary (${secEmail}): it may already exist. Set SEED_SECRETARY_EMAIL to an unused address and re-run.`);
  }
  const sl = await ok("POST", "/auth/login", { body: { email: secEmail, password: inv.data.temporaryPassword, organizationId: org } });
  const enr = await ok("POST", "/auth/mfa/enroll", { token: sl.accessToken });
  const conf = await ok("POST", "/auth/mfa/confirm", { token: sl.accessToken, body: { code: totp(enr.secret) } });
  secretaryToken = conf.accessToken;
  log(`  created throwaway secretary ${secEmail} to record votes`);
}
const deadline = new Date(Date.now() + 30 * 86400000).toISOString();
for (const d of todo) {
  const r = await ok("POST", `${P}/resolutions`, { token, body: {
    title: d.title, titleAr: d.titleAr, description: d.description, threshold: d.threshold, quorumPct: d.quorumPct, votingDeadline: deadline } });
  if (d.end !== "DRAFT") await ok("POST", `${P}/resolutions/${r.id}/open`, { token });
  for (const [who, choice] of d.votes) {
    await ok("POST", `${P}/resolutions/${r.id}/votes`, { token: secretaryToken, body: { vote: choice, shareholderId: holders[who].id } });
  }
  if (d.end === "PASSED" || d.end === "REJECTED") {
    const closed = await ok("POST", `${P}/resolutions/${r.id}/close`, { token });
    log(`  resolution "${d.title}" closed: ${closed.status}`);
  } else {
    log(`  resolution "${d.title}" left ${d.end}`);
  }
}

// ---- succession rules (left as drafts so they can be previewed and executed from the UI) ---------------

const ruleDefs = [
  { name: "Founder death: Founder Shares to the heir", trigger: "FOUNDER_DEATH", subject: "Fatima Al Noor", from: "Founder Shares", to: "Ordinary Voting", num: 1, den: 1, beneficiary: "Layla Haddad", legalReference: "Articles of association, art. 14" },
  { name: "IPO: convert Series A to ordinary shares", trigger: "IPO", from: "Series A Preferred", to: "Ordinary Voting", num: 2, den: 1, legalReference: "Shareholders agreement, clause 9.2" },
  { name: "Founder exit: Layla's non-voting shares become voting", trigger: "FOUNDER_EXIT", subject: "Layla Haddad", from: "Non-Voting Economic", to: "Ordinary Voting", num: 1, den: 1, legalReference: "Articles of association, art. 21" },
];
const haveRules = new Set((await ok("GET", `${P}/succession-rules`, { token })).map((r) => r.name));
for (const d of ruleDefs) {
  if (haveRules.has(d.name)) continue;
  await ok("POST", `${P}/succession-rules`, { token, body: {
    name: d.name, trigger: d.trigger, subjectId: d.subject && holders[d.subject].id, fromClassId: classes[d.from].id, toClassId: classes[d.to].id,
    ratioNumerator: d.num, ratioDenominator: d.den, beneficiaryId: d.beneficiary && holders[d.beneficiary].id, legalReference: d.legalReference } });
  log(`  succession rule drafted: ${d.name}`);
}

// ---- vault documents ---------------------------------------------------------------------------------

function tinyPdf(title, lines) {
  const esc = (s) => s.replace(/[\\()]/g, "\\$&");
  const text = ["BT /F1 16 Tf 56 780 Td (" + esc(title) + ") Tj ET",
    ...lines.map((l, i) => `BT /F1 11 Tf 56 ${740 - i * 18} Td (${esc(l)}) Tj ET`)].join("\n");
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
const docDefs = [
  ["Articles of Association", "ARTICLES_OF_ASSOCIATION", "articles-of-association.pdf", ["Demo document generated by the seed script.", "Article 14: succession of founder shares."]],
  ["Memorandum of Association", "MEMORANDUM", "memorandum-of-association.pdf", ["Demo document generated by the seed script."]],
  ["Notary receipt: share transfer", "NOTARY_RECEIPT", "notary-receipt.pdf", ["Demo notary receipt, reference NR-2026-0417."]],
  ["Shareholders agreement", "SHAREHOLDER_AGREEMENT", "shareholders-agreement.pdf", ["Demo agreement.", "Clause 9.2: conversion of Series A on IPO."]],
  ["Board minutes: 2027 budget", "RESOLUTION_MINUTES", "board-minutes-budget.pdf", ["Minutes of the vote approving the 2027 budget."]],
  ["Commercial licence", "LICENCE", "commercial-licence.pdf", ["Demo trade licence copy."]],
];
const haveDocs = new Set((await ok("GET", `${P}/vault`, { token })).map((d) => d.title));
for (const [title, category, file, lines] of docDefs) {
  if (haveDocs.has(title)) continue;
  const form = new FormData();
  form.append("title", title);
  form.append("category", category);
  form.append("file", new Blob([tinyPdf(title, lines)], { type: "application/pdf" }), file);
  await ok("POST", `${P}/vault`, { token, form });
  log(`  vault document added: ${title}`);
}

log("\nDone. Open the app and look at: Cap table, Ownership, UBO, Resolutions, Succession, Vault, Certificates and Audit.");
