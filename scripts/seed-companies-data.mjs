/**
 * The demo world loaded by seed-companies.mjs: five companies in four jurisdictions, 18 people, and the history of each register
 * from incorporation to today. Everything here is fictional: names, licence numbers, funds and email domains (all *.test).
 *
 * Shape of a company:
 *   key, signup (organization fields), founder (person key), colour (logo), members [{ person, role, holder? }],
 *   classes, holders, issuances (in date order), options, exercises, transfers, buybacks, owners (UBO chains), resolutions,
 *   succession, vault, filings, approvals, signatures, imports.
 * People are defined once in PEOPLE and referenced by key, so one login can belong to several companies.
 */

export const PEOPLE = {
  omar: { email: "omar@qamar.test", name: "Omar Farouk" },
  hessa: { email: "hessa@tamkeen.test", name: "Hessa Al Marzooqi" },
  james: { email: "james@falconridge.test", name: "James Whitfield" },
  mona: { email: "mona@qamar.test", name: "Mona Al Zaabi" },
  elena: { email: "elena@crescentaudit.test", name: "Elena Petrova" },
  priya: { email: "priya@qamar.test", name: "Priya Raman" },
  tariq: { email: "tariq@nasser.test", name: "Tariq Nasser" },
  nadia: { email: "nadia@saharahealth.test", name: "Dr. Nadia Al Hammadi" },
  farah: { email: "farah@saharahealth.test", name: "Farah Siddiqui" },
  khalid: { email: "khalid@saharahealth.test", name: "Khalid Al Rumaithi" },
  saeed: { email: "saeed@alwaha.test", name: "Saeed Rashid Al Falasi" },
  maryam: { email: "maryam@alwaha.test", name: "Maryam Al Falasi" },
  hamad: { email: "hamad@alwaha.test", name: "Hamad Al Falasi" },
  faisal: { email: "faisal@rimal.test", name: "Faisal Al Otaibi" },
  abeer: { email: "abeer@rimal.test", name: "Abeer Al Qahtani" },
  sultan: { email: "sultan@rimal.test", name: "Sultan Al Harbi" },
  lama: { email: "lama@rimal.test", name: "Lama Al Shehri" },
  lina: { email: "lina@baytcoffee.test", name: "Lina Saab" },
};

// Helpers for holder records.
const person = (fullNameEn, fullNameAr, nationality, dateOfBirth, extra = {}) => ({ holderType: "INDIVIDUAL", fullNameEn, fullNameAr, nationality, dateOfBirth, ...extra });
const corp = (fullNameEn, fullNameAr, nationality, registrationNo, address) => ({ holderType: "CORPORATE", fullNameEn, fullNameAr, nationality, registrationNo, address });

// ============================================================================================================
// 1. Qamar Logistics Holdings Ltd: ADGM, USD. Venture-backed, seed + Series A, ESOP, SAFE, venture-debt warrant.
// ============================================================================================================
const qamar = {
  key: "qamar",
  signup: { legalNameEn: "Qamar Logistics Holdings Ltd", legalNameAr: "قمر للخدمات اللوجستية القابضة المحدودة", jurisdiction: "ADGM",
    commercialLicenseNo: "000004821", baseCurrency: "USD", certificatePrefix: "QLH" },
  founder: "omar",
  colour: [30, 64, 175],
  members: [
    { person: "hessa", role: "CORPORATE_SECRETARY" },
    { person: "james", role: "BOARD_MEMBER" },
    { person: "mona", role: "BOARD_MEMBER" },
    { person: "elena", role: "LEGAL_AUDITOR" },
    { person: "priya", role: "SHAREHOLDER", holder: "Priya Raman" },
    { person: "tariq", role: "SHAREHOLDER", holder: "Tariq Nasser" },
  ],
  classes: [
    { name: "Ordinary", nameAr: "أسهم عادية", classType: "COMMON_VOTING", parValue: 0.001, currency: "USD", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 20_000_000 },
    { name: "Seed Preferred", nameAr: "أسهم ممتازة - البذرة", classType: "PREFERRED_FOUNDER", parValue: 0.001, currency: "USD", votingMultiplier: 1, seniorityRank: 2, liquidationMultiple: 1, participating: false, authorizedShares: 2_000_000 },
    { name: "Series A Preferred", nameAr: "أسهم ممتازة - الفئة أ", classType: "PREFERRED_FOUNDER", parValue: 0.001, currency: "USD", votingMultiplier: 1, seniorityRank: 3, liquidationMultiple: 1, participating: false, authorizedShares: 3_000_000 },
    { name: "Treasury", nameAr: "أسهم الخزينة", classType: "TREASURY", parValue: 0.001, currency: "USD", votingMultiplier: 0, seniorityRank: 1, authorizedShares: 500_000 },
  ],
  holders: [
    person("Omar Farouk", "عمر فاروق", "EG", "1986-05-14", { passportNo: "A23918874", address: "Apt 2104, Sun Tower, Al Reem Island, Abu Dhabi", email: "omar@qamar.test" }),
    person("Priya Raman", "بريا رامان", "IN", "1988-11-02", { passportNo: "Z4812290", address: "Villa 12, Al Raha Gardens, Abu Dhabi", email: "priya@qamar.test" }),
    person("Tariq Nasser", "طارق ناصر", "JO", "1979-02-21", { passportNo: "N0938471", nationalId: "784-1979-4410382-6", address: "Villa E-44, Emirates Hills, Dubai", email: "tariq@nasser.test" }),
    person("Dana Kassem", "دانا قاسم", "LB", "1983-08-30", { passportNo: "LR1184420", address: "Apt 3402, Marina Gate 1, Dubai Marina", email: "dana.kassem@mail.test" }),
    person("Rania Farouk", "رانيا فاروق", "EG", "1989-09-10", { passportNo: "A27730015", address: "Apt 2104, Sun Tower, Al Reem Island, Abu Dhabi" }),
    person("Karim Haddad", "كريم حداد", "LB", "1990-01-17", { passportNo: "LR2093318", address: "Apt 806, Shams Gate, Al Reem Island, Abu Dhabi", email: "karim@qamar.test" }),
    person("Sneha Iyer", "سنيها آير", "IN", "1993-06-09", { passportNo: "V7721093", address: "Apt 1412, Marina Square, Al Reem Island, Abu Dhabi", email: "sneha@qamar.test" }),
    person("Youssef El Amrani", "يوسف العمراني", "MA", "1991-12-03", { passportNo: "TK5520194", address: "Apt 302, Al Muneera, Al Raha Beach, Abu Dhabi", email: "youssef@qamar.test" }),
    person("Fatima Zahra Benali", "فاطمة الزهراء بنعلي", "MA", "1995-04-22", { passportNo: "UB3318820", address: "Apt 1907, Gate Tower 2, Al Reem Island, Abu Dhabi", email: "fatimazahra@qamar.test" }),
    person("Majid Al Suwaidi", "ماجد السويدي", "AE", "1981-10-11", { nationalId: "784-1981-2201746-3", address: "Villa 7, Al Bateen, Abu Dhabi" }),
    person("Hana Yamamoto", null, "JP", "1985-03-29", { passportNo: "TR4410028", address: "Apt 55, Jumeirah Living, Dubai" }),
    corp("Farouk Family Holdings Ltd", "فاروق العائلية القابضة المحدودة", "AE", "000007731", "Floor 15, Al Sarab Tower, ADGM Square, Al Maryah Island, Abu Dhabi"),
    corp("Falcon Ridge Ventures I LP", "صندوق فالكون ريدج فينتشرز الأول", "KY", "MC-301284", "PO Box 1093, Boundary Hall, Grand Cayman"),
    corp("Wadi Angels Syndicate SPV Ltd", "وادي إنجلز سينديكيت ش.غ.أ المحدودة", "AE", "000006402", "Floor 22, Al Khatem Tower, ADGM Square, Abu Dhabi"),
    corp("Meridian Gulf Growth Fund II LP", "صندوق ميريديان الخليج للنمو الثاني", "AE", "CL4417", "Level 9, Gate Village 3, DIFC, Dubai"),
    corp("Gulf Horizon Investment Company PJSC", "شركة أفق الخليج للاستثمار ش.م.ع", "AE", "CN-1103382", "Floor 31, Al Hilal Tower, Corniche Road, Abu Dhabi"),
    corp("Atlas Venture Debt Ltd", null, "GB", "12873301", "20 Grosvenor Street, London W1K 4QJ"),
    corp("Sandstone Angels SPV Ltd", "ساندستون إنجلز ش.غ.أ المحدودة", "AE", "000008115", "Floor 4, Hub71, Al Khatem Tower, Abu Dhabi"),
    person("Ali Hassan", "علي حسن", "SD", "1994-07-30", { passportNo: "P09183342", address: "Apt 1210, Sky Tower, Al Reem Island, Abu Dhabi", email: "ali.hassan@qamar.test" }),
    person("Mei Lin Chen", null, "SG", "1992-02-14", { passportNo: "K2281937E", address: "Apt 402, Al Bandar, Al Raha Beach, Abu Dhabi", email: "meilin@qamar.test" }),
  ],
  // [holder, class, quantity, price, date, instrument?]
  issuances: [
    ["Omar Farouk", "Ordinary", 4_200_000, 0.001, "2019-03-12"],
    ["Priya Raman", "Ordinary", 2_800_000, 0.001, "2019-03-12"],
    ["Tariq Nasser", "Ordinary", 350_000, 0.40, "2019-11-04"],
    ["Dana Kassem", "Ordinary", 200_000, 0.40, "2019-11-04"],
    ["Falcon Ridge Ventures I LP", "Seed Preferred", 1_250_000, 1.20, "2021-02-18"],
    ["Wadi Angels Syndicate SPV Ltd", "Seed Preferred", 416_667, 1.20, "2021-02-18"],
    ["Meridian Gulf Growth Fund II LP", "Series A Preferred", 1_558_442, 3.85, "2023-06-27"],
    ["Falcon Ridge Ventures I LP", "Series A Preferred", 389_610, 3.85, "2023-06-27"],
    ["Gulf Horizon Investment Company PJSC", "Series A Preferred", 519_481, 3.85, "2023-06-27"],
    ["Atlas Venture Debt Ltd", "Series A Preferred", 52_000, 3.85, "2024-02-01", "WARRANT"],
    ["Sandstone Angels SPV Ltd", "Series A Preferred", 111_111, 4.50, "2025-09-15", "CONVERTIBLE"],
  ],
  // ESOP: [holder, class, quantity, strike, grant date, cliff months, vesting months]
  options: [
    ["Karim Haddad", "Ordinary", 120_000, 0.40, "2020-06-01", 12, 48],
    ["Sneha Iyer", "Ordinary", 80_000, 1.20, "2021-09-15", 12, 48],
    ["Youssef El Amrani", "Ordinary", 60_000, 1.20, "2022-03-01", 12, 48],
    ["Fatima Zahra Benali", "Ordinary", 40_000, 3.85, "2024-04-15", 12, 48],
  ],
  exercises: [["Karim Haddad", 60_000, "2024-07-01"], ["Sneha Iyer", 20_000, "2025-05-10"]],
  // [from, to, class, quantity, price, date, reference]
  transfers: [
    ["Omar Farouk", "Farouk Family Holdings Ltd", "Ordinary", 600_000, 0.001, "2022-05-10", "Transfer to founder holding company, board consent 2022-04-28"],
    ["Dana Kassem", "Falcon Ridge Ventures I LP", "Ordinary", 100_000, 5.10, "2024-08-20", "Secondary sale, SPA dated 20 Aug 2024"],
  ],
  buybacks: [["Karim Haddad", "Ordinary", 15_000, 5.10, "2025-03-31", "Leaver buy-back under ESOP rules, clause 11.3"]],
  owners: {
    "Farouk Family Holdings Ltd": [["Omar Farouk", 90], ["Rania Farouk", 10]],
    "Wadi Angels Syndicate SPV Ltd": [["Tariq Nasser", 40], ["Dana Kassem", 20], ["Majid Al Suwaidi", 20], ["Hana Yamamoto", 20]],
  },
  resolutions: [
    { title: "Approve the FY2025 audited financial statements", titleAr: "اعتماد البيانات المالية المدققة للسنة المالية 2025",
      description: "Audited accounts for the year ended 31 December 2025 as presented by Crescent Audit: revenue USD 14.2m, EBITDA USD -1.1m.",
      threshold: "ORDINARY", end: "PASSED",
      votes: [["Omar Farouk", "FOR"], ["Priya Raman", "FOR"], ["Falcon Ridge Ventures I LP", "FOR"], ["Meridian Gulf Growth Fund II LP", "FOR"], ["Gulf Horizon Investment Company PJSC", "ABSTAIN"]] },
    { title: "Increase the ESOP pool by 500,000 ordinary shares", titleAr: "زيادة مجمع خيارات الموظفين بمقدار 500,000 سهم عادي",
      description: "Top-up ahead of the 2026 hiring plan (42 new roles). Two-thirds majority required under the shareholders agreement, clause 7.4.",
      threshold: "TWO_THIRDS", end: "PASSED",
      votes: [["Omar Farouk", "FOR"], ["Priya Raman", "FOR"], ["Meridian Gulf Growth Fund II LP", "FOR"], ["Falcon Ridge Ventures I LP", "FOR"], ["Wadi Angels Syndicate SPV Ltd", "AGAINST"]] },
    { title: "Approve a founder secondary sale of 300,000 ordinary shares", titleAr: "الموافقة على بيع ثانوي لـ 300,000 سهم عادي من المؤسس",
      description: "Omar Farouk proposes selling 300,000 ordinary shares to Meridian Gulf at USD 6.20. Special resolution (75%).",
      threshold: "SPECIAL", end: "REJECTED",
      votes: [["Omar Farouk", "FOR"], ["Farouk Family Holdings Ltd", "FOR"], ["Priya Raman", "AGAINST"], ["Gulf Horizon Investment Company PJSC", "AGAINST"], ["Falcon Ridge Ventures I LP", "AGAINST"]] },
    { title: "Approve the Series B term sheet led by Meridian Gulf", titleAr: "اعتماد ورقة شروط الجولة ب بقيادة ميريديان الخليج",
      description: "USD 22m at a USD 95m pre-money valuation, 1x non-participating preference, 10% post-money option pool.",
      threshold: "SPECIAL", end: "OPEN", days: 9,
      votes: [["Omar Farouk", "FOR"], ["Meridian Gulf Growth Fund II LP", "FOR"], ["Farouk Family Holdings Ltd", "FOR"]],
      portalVotes: [["priya", "FOR"]] },
    { title: "Appoint Crescent Audit as external auditor for FY2026", titleAr: "تعيين كريسنت للتدقيق مدققاً خارجياً للسنة المالية 2026",
      description: "Fee USD 48,000. Rotation of the engagement partner confirmed.",
      threshold: "ORDINARY", end: "OPEN", days: 4,
      votes: [["Omar Farouk", "FOR"]], portalVotes: [["tariq", "FOR"]] },
    { title: "Relocate the registered office to Al Maryah Island", titleAr: "نقل المكتب المسجل إلى جزيرة الماريه",
      description: "Move from Hub71 to Floor 18, Al Sila Tower, ADGM Square. Draft for the next board meeting.", threshold: "ORDINARY", end: "DRAFT", votes: [] },
  ],
  succession: [
    { name: "IPO: Seed Preferred converts to Ordinary", trigger: "IPO", from: "Seed Preferred", to: "Ordinary", num: 1, den: 1, legalReference: "Shareholders agreement, clause 14.1 (automatic conversion on a qualified IPO)" },
    { name: "IPO: Series A Preferred converts to Ordinary", trigger: "IPO", from: "Series A Preferred", to: "Ordinary", num: 1, den: 1, legalReference: "Shareholders agreement, clause 14.1" },
  ],
  vault: [
    ["Articles of Association (amended 27 Jun 2023)", "ARTICLES_OF_ASSOCIATION", ["ADGM model articles as amended at Series A close.", "Art. 32: transmission of shares on death."]],
    ["ADGM Commercial Licence 2026", "LICENCE", ["Licence no. 000004821, valid to 11 March 2027.", "Activity: holding company, logistics technology."]],
    ["Series A Shareholders Agreement", "SHAREHOLDER_AGREEMENT", ["Dated 27 June 2023 between the company, the founders, Meridian Gulf, Falcon Ridge and Gulf Horizon.", "Clause 7.4: reserved matters. Clause 14.1: conversion on IPO."]],
    ["Board minutes: FY2025 accounts", "RESOLUTION_MINUTES", ["Meeting of 14 April 2026 approving the FY2025 audited accounts."]],
    ["Share transfer instrument: Dana Kassem to Falcon Ridge", "NOTARY_RECEIPT", ["Stock transfer form, 100,000 ordinary shares at USD 5.10, dated 20 August 2024."]],
    ["ESOP Plan Rules 2020", "OTHER", ["Employee share option plan adopted 1 June 2020.", "Clause 11.3: leaver buy-back at fair market value."]],
  ],
  filings: [],
  approvalsOn: true,
  // Proposed by the secretary once approval is on. decide: [person, "approve" | "reject", note] or none (left pending).
  approvals: [
    { kind: "issue", holder: "Mei Lin Chen", cls: "Ordinary", instrument: "OPTION", quantity: 30_000, price: 5.10, date: "2026-09-01", decide: ["james", "approve", "Within the pool approved by the board."] },
    { kind: "issue", holder: "Ali Hassan", cls: "Ordinary", instrument: "OPTION", quantity: 25_000, price: 5.10, date: "2026-10-15" },
    { kind: "transfer", from: "Tariq Nasser", to: "Wadi Angels Syndicate SPV Ltd", cls: "Ordinary", quantity: 50_000, price: 5.50, date: "2026-10-28", reference: "Angel consolidation into the SPV" },
    { kind: "exercise", holder: "Youssef El Amrani", quantity: 10_000, date: "2026-10-20", decide: ["mona", "reject", "Exercise window is closed until the Series B completes."] },
  ],
  signatures: [
    { person: "hessa", type: "RESOLUTION", resolution: "Approve the FY2025 audited financial statements", statement: "Certified true copy, company secretary" },
    { person: "james", type: "RESOLUTION", resolution: "Increase the ESOP pool by 500,000 ordinary shares", statement: "Signed as investor director" },
    { person: "priya", type: "CERTIFICATE", holder: "Priya Raman", statement: "Acknowledged by the holder" },
  ],
  apiKey: "Finance reporting (read-only)",
};

// ============================================================================================================
// 2. Sahara Health Labs Limited: DIFC, USD. Seed-stage health tech.
// ============================================================================================================
const sahara = {
  key: "sahara",
  signup: { legalNameEn: "Sahara Health Labs Limited", legalNameAr: "صحارى هيلث لابز المحدودة", jurisdiction: "DIFC",
    commercialLicenseNo: "CL5932", baseCurrency: "USD", certificatePrefix: "SHL" },
  founder: "nadia",
  colour: [190, 18, 60],
  members: [
    { person: "farah", role: "CORPORATE_SECRETARY" },
    { person: "khalid", role: "BOARD_MEMBER" },
    { person: "tariq", role: "SHAREHOLDER", holder: "Tariq Nasser" },
  ],
  classes: [
    { name: "Ordinary", nameAr: "أسهم عادية", classType: "COMMON_VOTING", parValue: 0.0001, currency: "USD", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 10_000_000 },
    { name: "Seed Preferred", nameAr: "أسهم ممتازة - البذرة", classType: "PREFERRED_FOUNDER", parValue: 0.0001, currency: "USD", votingMultiplier: 1, seniorityRank: 2, liquidationMultiple: 1, participating: false, authorizedShares: 1_500_000 },
  ],
  holders: [
    person("Dr. Nadia Al Hammadi", "د. نادية الحمادي", "AE", "1984-03-19", { nationalId: "784-1984-6630218-1", address: "Villa 31, Al Barsha South 2, Dubai", email: "nadia@saharahealth.test" }),
    person("Rohan Mehta", "روهان ميهتا", "IN", "1987-07-07", { passportNo: "M8812043", address: "Apt 2207, Index Tower, DIFC, Dubai", email: "rohan@saharahealth.test" }),
    person("Tariq Nasser", "طارق ناصر", "JO", "1979-02-21", { passportNo: "N0938471", nationalId: "784-1979-4410382-6", address: "Villa E-44, Emirates Hills, Dubai", email: "tariq@nasser.test" }),
    person("Hiba Mansour", "هبة منصور", "SY", "1992-05-25", { passportNo: "N014420981", address: "Apt 904, Bay Square 7, Business Bay, Dubai", email: "hiba@saharahealth.test" }),
    person("Daniel Okafor", null, "NG", "1990-11-30", { passportNo: "A09921837", address: "Apt 1503, Burj Vista 1, Downtown Dubai", email: "daniel@saharahealth.test" }),
    person("Abdulla Al Noor", "عبدالله النور", "AE", "1962-08-14", { nationalId: "784-1962-1028846-2", address: "Villa 2, Street 17, Jumeirah 1, Dubai" }),
    person("Shaikha Al Noor", "شيخة النور", "AE", "1966-12-01", { nationalId: "784-1966-3319024-8", address: "Villa 2, Street 17, Jumeirah 1, Dubai" }),
    corp("Oasis Angel Network SPV Ltd", "شبكة الواحة للمستثمرين ش.غ.أ المحدودة", "AE", "CL6120", "Unit 802, Level 8, Gate Village 10, DIFC, Dubai"),
    corp("Sandstone Capital Fund I LP", "صندوق ساندستون كابيتال الأول", "AE", "CL5207", "Level 3, ICD Brookfield Place, DIFC, Dubai"),
    corp("Al Noor Family Office Ltd", "المكتب العائلي للنور المحدودة", "AE", "CL3381", "Level 14, Central Park Towers, DIFC, Dubai"),
  ],
  issuances: [
    ["Dr. Nadia Al Hammadi", "Ordinary", 3_000_000, 0.0001, "2022-01-20"],
    ["Rohan Mehta", "Ordinary", 2_000_000, 0.0001, "2022-01-20"],
    ["Tariq Nasser", "Ordinary", 150_000, 0.50, "2022-07-12"],
    ["Oasis Angel Network SPV Ltd", "Ordinary", 300_000, 0.50, "2022-07-12"],
    ["Sandstone Capital Fund I LP", "Seed Preferred", 857_143, 1.75, "2024-03-14"],
    ["Al Noor Family Office Ltd", "Seed Preferred", 285_714, 1.75, "2024-03-14"],
  ],
  options: [
    ["Hiba Mansour", "Ordinary", 90_000, 0.50, "2022-10-01", 12, 48],
    ["Daniel Okafor", "Ordinary", 60_000, 1.75, "2024-06-01", 12, 48],
  ],
  exercises: [["Hiba Mansour", 30_000, "2025-02-03"]],
  transfers: [
    ["Oasis Angel Network SPV Ltd", "Tariq Nasser", "Ordinary", 100_000, 0.50, "2025-06-30", "In-specie distribution from Oasis SPV to its members"],
  ],
  buybacks: [],
  owners: {
    "Al Noor Family Office Ltd": [["Abdulla Al Noor", 70], ["Shaikha Al Noor", 30]],
    // 35% recorded: the rest of the SPV is spread over 14 small angels, so the UBO report shows a gap to explain.
    "Oasis Angel Network SPV Ltd": [["Tariq Nasser", 35]],
  },
  resolutions: [
    { title: "Approve the DHA clinical licence budget", titleAr: "اعتماد ميزانية ترخيص هيئة الصحة بدبي",
      description: "USD 380,000 for the Dubai Health Authority telehealth licence, clinical lead hires and the ISO 27001 audit.",
      threshold: "ORDINARY", end: "PASSED",
      votes: [["Dr. Nadia Al Hammadi", "FOR"], ["Rohan Mehta", "FOR"], ["Sandstone Capital Fund I LP", "FOR"], ["Al Noor Family Office Ltd", "FOR"]] },
    { title: "Adopt the 2026 data protection policy", titleAr: "اعتماد سياسة حماية البيانات لعام 2026",
      description: "Policy under DIFC Data Protection Law No. 5 of 2020, with a named DPO and a 72-hour breach notice procedure.",
      threshold: "ORDINARY", end: "OPEN", days: 12,
      votes: [["Dr. Nadia Al Hammadi", "FOR"]], portalVotes: [["tariq", "FOR"]] },
    { title: "Open a Riyadh branch", titleAr: "افتتاح فرع في الرياض",
      description: "Branch registration with MISA and a SAR 2m first-year budget.", threshold: "TWO_THIRDS", end: "DRAFT", votes: [] },
  ],
  succession: [
    { name: "IPO: Seed Preferred converts to Ordinary", trigger: "IPO", from: "Seed Preferred", to: "Ordinary", num: 1, den: 1, legalReference: "Seed subscription agreement, clause 9" },
  ],
  vault: [
    ["Articles of Association", "ARTICLES_OF_ASSOCIATION", ["DIFC private company articles adopted 20 January 2022."]],
    ["DIFC Commercial Licence", "LICENCE", ["Licence CL5932, valid to 19 January 2027."]],
    ["Seed Subscription and Shareholders Agreement", "SHAREHOLDER_AGREEMENT", ["Dated 14 March 2024 with Sandstone Capital and Al Noor Family Office."]],
    ["Board minutes: DHA licence budget", "RESOLUTION_MINUTES", ["Meeting of 2 September 2026."]],
  ],
  filings: [["DIFC_UBO_CHANGE", "2025-07-21", "DIFC-BO-2025-07-0419"]],
  approvalsOn: false,
  approvals: [],
  signatures: [{ person: "farah", type: "RESOLUTION", resolution: "Approve the DHA clinical licence budget", statement: "Certified by the company secretary" }],
};

// ============================================================================================================
// 3. Al Waha General Trading LLC: Mainland UAE (Dubai), AED. Family business since 1998, succession and UBO heavy.
// ============================================================================================================
const alwaha = {
  key: "alwaha",
  signup: { legalNameEn: "Al Waha General Trading LLC", legalNameAr: "الواحة للتجارة العامة ذ.م.م", jurisdiction: "MAINLAND_UAE",
    commercialLicenseNo: "512874", baseCurrency: "AED", certificatePrefix: "AWT" },
  founder: "saeed",
  colour: [21, 128, 61],
  members: [
    { person: "hessa", role: "CORPORATE_SECRETARY" },
    { person: "maryam", role: "BOARD_MEMBER" },
    { person: "hamad", role: "SHAREHOLDER", holder: "Hamad Saeed Al Falasi" },
  ],
  classes: [
    { name: "Ordinary Shares", nameAr: "حصص عادية", classType: "COMMON_VOTING", parValue: 1000, currency: "AED", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 3_000 },
    { name: "Profit Participation Shares", nameAr: "حصص المشاركة في الأرباح", classType: "NON_VOTING_ECONOMIC", parValue: 1000, currency: "AED", votingMultiplier: 0, seniorityRank: 1, authorizedShares: 3_000 },
  ],
  holders: [
    person("Saeed Rashid Al Falasi", "سعيد راشد الفلاسي", "AE", "1958-01-05", { nationalId: "784-1958-0417733-5", address: "Villa 14, Street 23, Umm Suqeim 2, Dubai", email: "saeed@alwaha.test" }),
    person("Moza Al Falasi", "موزة الفلاسي", "AE", "1962-06-18", { nationalId: "784-1962-0882190-4", address: "Villa 14, Street 23, Umm Suqeim 2, Dubai" }),
    person("Maryam Saeed Al Falasi", "مريم سعيد الفلاسي", "AE", "1984-09-27", { nationalId: "784-1984-2209431-7", address: "Villa 8, Al Barari, Dubai", email: "maryam@alwaha.test" }),
    person("Hamad Saeed Al Falasi", "حمد سعيد الفلاسي", "AE", "1988-02-12", { nationalId: "784-1988-5531207-9", address: "Villa 61, Arabian Ranches 2, Dubai", email: "hamad@alwaha.test" }),
    person("Joseph D'Souza", "جوزيف دي سوزا", "IN", "1971-10-08", { passportNo: "J4419082", address: "Apt 1105, Al Mankhool Tower, Bur Dubai", email: "joseph@alwaha.test" }),
    corp("Al Falasi Family Holding LLC", "الفلاسي العائلية القابضة ذ.م.م", "AE", "733109", "Office 701, Al Ghurair Centre, Deira, Dubai"),
  ],
  issuances: [
    ["Saeed Rashid Al Falasi", "Ordinary Shares", 3_000, 1000, "1998-04-12"],
    ["Joseph D'Souza", "Profit Participation Shares", 150, 1000, "2019-01-01"],
  ],
  options: [],
  exercises: [],
  transfers: [
    ["Saeed Rashid Al Falasi", "Al Falasi Family Holding LLC", "Ordinary Shares", 750, 1000, "2016-09-01", "Notarised transfer, Dubai Courts Notary Public 2016/88412"],
    ["Saeed Rashid Al Falasi", "Maryam Saeed Al Falasi", "Ordinary Shares", 360, 1000, "2021-01-10", "Gift to daughter, notarised 2021/1104"],
    ["Saeed Rashid Al Falasi", "Hamad Saeed Al Falasi", "Ordinary Shares", 360, 1000, "2021-01-10", "Gift to son, notarised 2021/1105"],
  ],
  buybacks: [],
  owners: {
    "Al Falasi Family Holding LLC": [["Saeed Rashid Al Falasi", 40], ["Moza Al Falasi", 20], ["Maryam Saeed Al Falasi", 20], ["Hamad Saeed Al Falasi", 20]],
  },
  resolutions: [
    { title: "Distribute an AED 1,200,000 dividend for FY2025", titleAr: "توزيع أرباح بقيمة 1,200,000 درهم عن السنة المالية 2025",
      description: "Paid pro rata to ordinary and profit participation shares on 15 November 2026.",
      threshold: "ORDINARY", end: "PASSED",
      votes: [["Saeed Rashid Al Falasi", "FOR"], ["Al Falasi Family Holding LLC", "FOR"], ["Maryam Saeed Al Falasi", "FOR"], ["Hamad Saeed Al Falasi", "FOR"]] },
    { title: "Amend the MoA to appoint Maryam Al Falasi as co-manager", titleAr: "تعديل عقد التأسيس لتعيين مريم الفلاسي مديراً مشاركاً",
      description: "Special resolution under Federal Decree-Law 32/2021, art. 75. Needs 75% of the capital present.",
      threshold: "SPECIAL", end: "OPEN", days: 6,
      votes: [["Saeed Rashid Al Falasi", "FOR"], ["Al Falasi Family Holding LLC", "FOR"]], portalVotes: [["hamad", "FOR"]] },
    { title: "Open a branch in Sharjah", titleAr: "افتتاح فرع في الشارقة",
      description: "Branch at Al Majaz waterfront for wholesale foodstuff trading.", threshold: "ORDINARY", end: "DRAFT", votes: [] },
  ],
  succession: [
    { name: "Founder death: Saeed's shares pass to the family holding as profit shares", trigger: "FOUNDER_DEATH", subject: "Saeed Rashid Al Falasi", from: "Ordinary Shares", to: "Profit Participation Shares", num: 1, den: 1, beneficiary: "Al Falasi Family Holding LLC", legalReference: "MoA art. 18; heirs hold through the family holding per the family charter 2020" },
    { name: "Founder incapacity: Saeed's votes fall away, economics stay", trigger: "FOUNDER_INCAPACITY", subject: "Saeed Rashid Al Falasi", from: "Ordinary Shares", to: "Profit Participation Shares", num: 1, den: 1, legalReference: "Family charter 2020, section 6.2" },
  ],
  vault: [
    ["Memorandum of Association (consolidated 2021)", "MEMORANDUM", ["Consolidated after the January 2021 share gifts.", "Art. 18: transmission on death."]],
    ["Dubai DED Trade Licence 512874", "LICENCE", ["General trading, foodstuff and household goods. Valid to 11 April 2027."]],
    ["Notary receipt 2021/1104", "NOTARY_RECEIPT", ["Gift of 360 shares to Maryam Saeed Al Falasi."]],
    ["Notary receipt 2021/1105", "NOTARY_RECEIPT", ["Gift of 360 shares to Hamad Saeed Al Falasi."]],
    ["General assembly minutes: FY2025 dividend", "RESOLUTION_MINUTES", ["Meeting of 20 October 2026."]],
  ],
  filings: [["UAE_LLC_PARTNERS_ANNUAL", "2026-01-20", "MOE-PR-2026-004412"], ["UAE_UBO_CHANGE", "2021-01-20", "MOE-UBO-2021-118930"]],
  approvalsOn: false,
  approvals: [],
  signatures: [{ person: "hessa", type: "RESOLUTION", resolution: "Distribute an AED 1,200,000 dividend for FY2025", statement: "Certified by the company secretary" }],
};

// ============================================================================================================
// 4. Rimal Financial Technology Company: KSA closed joint stock company, SAR. Opening register imported from a spreadsheet.
// ============================================================================================================
const rimal = {
  key: "rimal",
  signup: { legalNameEn: "Rimal Financial Technology Company", legalNameAr: "شركة رمال للتقنية المالية", jurisdiction: "KSA",
    commercialLicenseNo: "1010765432", baseCurrency: "SAR", certificatePrefix: "RFT" },
  founder: "faisal",
  colour: [180, 83, 9],
  members: [
    { person: "abeer", role: "CORPORATE_SECRETARY" },
    { person: "sultan", role: "BOARD_MEMBER" },
    { person: "james", role: "BOARD_MEMBER" },
    { person: "elena", role: "LEGAL_AUDITOR" },
    { person: "lama", role: "SHAREHOLDER", holder: "Lama Al Shehri" },
  ],
  classes: [
    { name: "Ordinary", nameAr: "أسهم عادية", classType: "COMMON_VOTING", parValue: 10, currency: "SAR", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 2_000_000 },
    { name: "Series A Preferred", nameAr: "أسهم ممتازة - الفئة أ", classType: "PREFERRED_FOUNDER", parValue: 10, currency: "SAR", votingMultiplier: 1, seniorityRank: 2, liquidationMultiple: 1, participating: false, authorizedShares: 500_000 },
    { name: "Treasury", nameAr: "أسهم الخزينة", classType: "TREASURY", parValue: 10, currency: "SAR", votingMultiplier: 0, seniorityRank: 1, authorizedShares: 100_000 },
  ],
  // Loaded through the CSV import, the way a company moving off a spreadsheet would start.
  imports: {
    holders: [
      "type,name_en,name_ar,nationality,id_number,registration_no,email",
      "INDIVIDUAL,Faisal Al Otaibi,فيصل العتيبي,SA,1043398821,,faisal@rimal.test",
      "INDIVIDUAL,Abdulrahman Al Ghamdi,عبدالرحمن الغامدي,SA,1055120934,,abdulrahman@rimal.test",
      "INDIVIDUAL,Lama Al Shehri,لمى الشهري,SA,1088302716,,lama@rimal.test",
      "INDIVIDUAL,Nawaf Al Dossary,نواف الدوسري,SA,1099214560,,nawaf@rimal.test",
      "INDIVIDUAL,Mohammed Al Qahtani,محمد القحطاني,SA,1029930418,,",
      "INDIVIDUAL,Sara Al Rasheed,سارة الرشيد,SA,1071180344,,",
      "CORPORATE,Riyadh Angels Holding Company,شركة رياض إنجلز القابضة,SA,,1010688120,",
    ],
    issuances: [
      "holder,share_class,quantity,price_per_share,date",
      "Faisal Al Otaibi,Ordinary,600000,10.00,2021-08-15",
      "Abdulrahman Al Ghamdi,Ordinary,300000,10.00,2021-08-15",
      "Lama Al Shehri,Ordinary,50000,10.00,2021-08-15",
      "Riyadh Angels Holding Company,Ordinary,100000,15.00,2022-04-03",
    ],
  },
  holders: [
    corp("Falcon Ridge Ventures I LP", "صندوق فالكون ريدج فينتشرز الأول", "KY", "MC-301284", "PO Box 1093, Boundary Hall, Grand Cayman"),
    corp("Najd Growth Partners Fund", "صندوق نجد لشركاء النمو", "SA", "7001488230", "Olaya Towers, Tower B, Floor 12, Riyadh"),
  ],
  issuances: [
    ["Falcon Ridge Ventures I LP", "Series A Preferred", 141_176, 42.50, "2024-11-05"],
    ["Najd Growth Partners Fund", "Series A Preferred", 94_118, 42.50, "2024-11-05"],
  ],
  options: [["Nawaf Al Dossary", "Ordinary", 20_000, 15.00, "2023-01-01", 12, 48]],
  exercises: [],
  transfers: [["Abdulrahman Al Ghamdi", "Lama Al Shehri", "Ordinary", 10_000, 38.00, "2025-12-02", "Secondary sale, notarised at the Riyadh notary 1447/3381"]],
  buybacks: [],
  owners: { "Riyadh Angels Holding Company": [["Mohammed Al Qahtani", 50], ["Sara Al Rasheed", 50]] },
  resolutions: [
    { title: "Approve the Series A investment agreement", titleAr: "اعتماد اتفاقية الاستثمار للجولة أ",
      description: "SAR 10m from Falcon Ridge and Najd Growth at SAR 42.50 per share. Special resolution of the extraordinary general assembly.",
      threshold: "SPECIAL", end: "PASSED",
      votes: [["Faisal Al Otaibi", "FOR"], ["Abdulrahman Al Ghamdi", "FOR"], ["Lama Al Shehri", "FOR"], ["Riyadh Angels Holding Company", "FOR"]] },
    { title: "Approve the 2027 operating budget of SAR 18.5m", titleAr: "اعتماد الميزانية التشغيلية لعام 2027 بقيمة 18.5 مليون ريال",
      description: "Includes the SAMA open banking licence costs and 24 new hires in Riyadh.",
      threshold: "ORDINARY", end: "OPEN", days: 14,
      votes: [["Faisal Al Otaibi", "FOR"], ["Falcon Ridge Ventures I LP", "FOR"]], portalVotes: [["lama", "ABSTAIN"]] },
    { title: "Apply to the SAMA regulatory sandbox", titleAr: "التقدم إلى البيئة التجريبية التشريعية لساما",
      description: "Cohort 9 application for the BNPL product.", threshold: "TWO_THIRDS", end: "DRAFT", votes: [] },
  ],
  succession: [
    { name: "Nomu listing: Series A converts to Ordinary", trigger: "IPO", from: "Series A Preferred", to: "Ordinary", num: 1, den: 1, legalReference: "Investment agreement, clause 12; Saudi Companies Law 2022, art. 114" },
  ],
  vault: [
    ["Bylaws (amended November 2024)", "ARTICLES_OF_ASSOCIATION", ["Closed joint stock company bylaws, amended for the Series A."]],
    ["Commercial Registration 1010765432", "LICENCE", ["Ministry of Commerce, Riyadh. Valid to 1448/02/14 H."]],
    ["Series A Investment Agreement", "SHAREHOLDER_AGREEMENT", ["Dated 5 November 2024."]],
    ["EGA minutes: Series A approval", "RESOLUTION_MINUTES", ["Extraordinary general assembly of 28 October 2024."]],
  ],
  filings: [["KSA_REGISTER_FILING", "2025-12-14", "MC-SHR-1447-06-2281"]],
  approvalsOn: true,
  approvals: [
    { kind: "transfer", from: "Faisal Al Otaibi", to: "Nawaf Al Dossary", cls: "Ordinary", quantity: 5_000, price: 38.00, date: "2026-10-26", reference: "Founder-to-employee sale, management incentive letter" },
    { kind: "issue", holder: "Nawaf Al Dossary", cls: "Ordinary", instrument: "OPTION", quantity: 10_000, price: 38.00, date: "2026-10-01", decide: ["sultan", "approve", "Annual refresh grant."] },
  ],
  signatures: [{ person: "abeer", type: "RESOLUTION", resolution: "Approve the Series A investment agreement", statement: "Certified by the board secretary" }],
};

// ============================================================================================================
// 5. Bayt Coffee Roasters LLC: Mainland UAE, AED. Omar's second company (same login, company switcher).
// ============================================================================================================
const bayt = {
  key: "bayt",
  secondCompanyOf: "omar",
  signup: { legalNameEn: "Bayt Coffee Roasters LLC", legalNameAr: "بيت لتحميص القهوة ذ.م.م", jurisdiction: "MAINLAND_UAE",
    commercialLicenseNo: "1094417", baseCurrency: "AED", certificatePrefix: "BCR" },
  founder: "omar",
  colour: [120, 53, 15],
  members: [{ person: "lina", role: "SHAREHOLDER", holder: "Lina Saab" }],
  classes: [
    { name: "Ordinary Shares", nameAr: "حصص عادية", classType: "COMMON_VOTING", parValue: 1000, currency: "AED", votingMultiplier: 1, seniorityRank: 1, authorizedShares: 300 },
  ],
  holders: [
    person("Omar Farouk", "عمر فاروق", "EG", "1986-05-14", { passportNo: "A23918874", address: "Apt 2104, Sun Tower, Al Reem Island, Abu Dhabi", email: "omar@qamar.test" }),
    person("Lina Saab", "لينا صعب", "LB", "1990-03-08", { passportNo: "LR3398102", address: "Apt 12, Al Wasl Road, Jumeirah, Dubai", email: "lina@baytcoffee.test" }),
  ],
  issuances: [
    ["Omar Farouk", "Ordinary Shares", 180, 1000, "2025-02-01"],
    ["Lina Saab", "Ordinary Shares", 120, 1000, "2025-02-01"],
  ],
  options: [], exercises: [], transfers: [], buybacks: [], owners: {}, succession: [], approvals: [], signatures: [],
  resolutions: [
    { title: "Open a second roastery in Al Quoz", titleAr: "افتتاح محمصة ثانية في القوز", description: "AED 650,000 fit-out, landlord deposit and a Probat roaster.",
      threshold: "ORDINARY", end: "OPEN", days: 10, votes: [], portalVotes: [["lina", "FOR"]] },
  ],
  vault: [
    ["Memorandum of Association", "MEMORANDUM", ["Two partners: Omar Farouk 60%, Lina Saab 40%."]],
    ["Dubai DED Trade Licence 1094417", "LICENCE", ["Coffee roasting and retail. Valid to 31 January 2027."]],
  ],
  filings: [],
  approvalsOn: false,
};

export const COMPANIES = [qamar, sahara, alwaha, rimal, bayt];
