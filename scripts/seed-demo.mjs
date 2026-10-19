#!/usr/bin/env node
/**
 * Fills an existing organization with realistic demo data so every screen has something to show:
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

import { signInFounder, log } from "./seed-lib.mjs";
import { seedBase } from "./seed-base.mjs";

const ctx = await signInFounder();
log(`Signed in. Seeding organization ${ctx.org}.`);
await seedBase(ctx);
log("\nDone. Open the app and look at: Cap table, Ownership, UBO, Resolutions, Succession, Vault, Certificates and Audit.");
