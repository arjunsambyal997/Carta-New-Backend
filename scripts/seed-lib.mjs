/** Shared helpers for the seed scripts: prompts, TOTP, a tiny API client and a few generated files. */
import crypto from "node:crypto";
import readline from "node:readline";
import zlib from "node:zlib";

export const API = (process.env.API_URL || "http://localhost:8080").replace(/\/$/, "") + "/api/v1";

export function ask(question, { hidden = false } = {}) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    if (hidden) {
      rl._writeToOutput = (s) => { if (s.includes(question)) process.stdout.write(s); };
    }
    rl.question(question, (answer) => { rl.close(); if (hidden) process.stdout.write("\n"); resolve(answer.trim()); });
  });
}

export const base32 = (s) => {
  const a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of s.replace(/=+$/, "")) bits += a.indexOf(c).toString(2).padStart(5, "0");
  const out = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) out.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(out);
};
export function totp(secret, offset = 0) {
  const b = Buffer.alloc(8);
  b.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000) + offset));
  const h = crypto.createHmac("sha1", base32(secret)).update(b).digest();
  const o = h[19] & 15;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1000000).padStart(6, "0");
}

export async function call(method, path, { body, token, form, raw } = {}) {
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

export async function ok(method, path, opts, expect = [200, 201, 204]) {
  const r = await call(method, path, opts);
  if (!expect.includes(r.status)) {
    const msg = typeof r.data === "object" && r.data ? (r.data.message || r.data.detail || r.data.error || JSON.stringify(r.data)) : r.data;
    throw new Error(`${method} ${path} -> ${r.status}: ${String(msg).slice(0, 300)}`);
  }
  return r.data;
}

export const log = (s) => console.log(s);


/** Signs in as the founder (password + authenticator code) and returns the API context the seed steps use. */
export async function signInFounder() {
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

  return { token, org, P, me };
}

/** A solid-colour PNG with a lighter band, used as a demo company logo. No image library needed. */
export function demoLogoPng(size = 128, rgb = [14, 116, 144]) {
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const sum = Buffer.alloc(4); sum.writeUInt32BE(crc(body));
    return Buffer.concat([len, body, sum]);
  };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 2;
  const rows = [];
  for (let y = 0; y < size; y++) {
    const row = Buffer.alloc(1 + size * 3);
    for (let x = 0; x < size; x++) {
      const band = Math.abs(x - y) < size / 8;
      const [r, g, b] = band ? [255, 255, 255] : rgb;
      row[1 + x * 3] = r; row[2 + x * 3] = g; row[3 + x * 3] = b;
    }
    rows.push(row);
  }
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(Buffer.concat(rows))), chunk("IEND", Buffer.alloc(0))]);
}
