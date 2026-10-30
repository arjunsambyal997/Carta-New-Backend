/** The shared demo register: classes, holders, issuances, ownership chains, resolutions, succession rules and vault documents. */
import crypto from "node:crypto";
import { call, ok, log, totp } from "./seed-lib.mjs";

/**
 * `ctx` = { token, org, P } from signInFounder(). `opts.secretaryToken` (an enrolled CORPORATE_SECRETARY's token) records the
 * votes; without it a throwaway secretary account is created just for that.
 */
export async function seedBase(ctx, opts = {}) {
  const { token, org, P } = ctx;
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
    classes[d.name] = await ok("POST", `${P}/share-classes`, { token, body: { participating: false, ...d } }); // Jackson 3 rejects a missing boolean
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
      dateOfBirth: type === "INDIVIDUAL" ? `${1965 + (en.length * 7) % 30}-0${1 + (en.length % 9)}-1${en.length % 9}` : undefined,
      address: type === "INDIVIDUAL" ? `Villa ${en.length}, Al Wasl Road, Dubai` : "Office 1204, Business Bay, Dubai",
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
  let secretaryToken = opts.secretaryToken || null;
  if (!secretaryToken && todo.some((d) => d.votes.length)) {
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

  return { classes, holders };
}
