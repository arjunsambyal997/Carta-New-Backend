#!/usr/bin/env node
/** Prints the current 6-digit code for a base32 authenticator secret: node scripts/totp.mjs <secret> */
import { totp } from "./seed-lib.mjs";
const secret = process.argv[2];
if (!secret) { console.error("usage: node scripts/totp.mjs <base32 secret>"); process.exit(1); }
console.log(totp(secret.replace(/\s+/g, "").toUpperCase()));
