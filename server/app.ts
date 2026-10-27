import http, { type IncomingMessage, type ServerResponse } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { DB } from './db.ts';
import type { Deps } from './actions.ts';
import { now, advance, resetClock } from './clock.ts';
import { seed, HERO_ID, hashPayer } from './seed.ts';
import { nextOpportunity, confirmOpportunity, declineOpportunity, shouldInterrupt, briefList, NotProposedError } from './opportunities.ts';
import { phraseFull } from './phrase.ts';
import { SHELF_IN_APP } from './lines.ts';
import { display } from './display.ts';
import { toSpoken } from './spoken.ts';
import { converse, DECLINED } from './agent.ts';
import { getPayLink, payLink, recordWalkin, recordCustomerPayment, expireLinks } from './payments.ts';
import { getLedger } from './ledger.ts';
import { getImpact } from './impact.ts';
import { publish, subscribe, poll } from './events.ts';
import { shelfFlow, cleanReadings, type Reading } from './shelf.ts';
import { getFacts } from './facts.ts';
import { proposeReminder } from './propose.ts';
import { getProfile, saveProfile, normalisePhone, ProfileError } from './profile.ts';

export type AppDeps = Deps & { demoKey?: string; demoPhone?: string; waVerifyToken?: string; pollMs?: number };
type Handler = (ctx: { db: DB; deps: AppDeps; params: string[]; query: URLSearchParams; body: any; res: ServerResponse }) => Promise<unknown> | unknown;

const CANNED_SHELF: Reading[] = [{ sku: 'surf_1kg', level: 'empty' }, { sku: 'parle_g', level: 'low' }];
const UNREADABLE = 'Photo saaf nahi dikhi. Shelf ki photo dobara kheenchiye.';

// Everything Munshi says carries its Devanagari when known, for the app's Hindi voice.
const announce = (text: string) => publish({ type: 'announce', text, spoken: toSpoken(text) });
const withSpoken = <T extends { line: string }>(r: T) => ({ ...r, spoken: toSpoken(r.line) });

function publishShelf(db: DB, readings: Reading[]) {
  const r = shelfFlow(db, HERO_ID, readings, now());
  publish({ type: 'shelf', ...r });
  return r;
}

// Tell the Soundbox once per link that ran out; the reminder is back on its list with a "naya link" line.
function sweepLinks(db: DB) {
  for (const x of expireLinks(db, now())) {
    publish({ type: 'expired', ...x });
    announce(`${x.name} ka pay link khatam ho gaya.`);
  }
}

// A shelf photo sent on WhatsApp: photos are read on the phone now, so say where to take it.
function handleWhatsApp(body: any) {
  const msg = body?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
  if (msg?.type === 'image') announce(SHELF_IN_APP);
}

const DIST = fileURLToPath(new URL('../web/dist/', import.meta.url));
const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.json': 'application/json', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json', '.woff2': 'font/woff2',
};

class HttpError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

const routes: Array<[string, RegExp, Handler]> = [
  ['GET', /^\/healthz$/, () => ({ ok: true })],
  ['GET', /^\/api\/events$/, ({ res }) => { subscribe(res); return undefined; }],
  ['GET', /^\/api\/events\/poll$/, ({ deps, query }) => {
    const raw = query.get('after');
    const after = raw === null ? undefined : Number(raw);
    if (after !== undefined && !Number.isFinite(after)) throw new HttpError(400, 'after must be a number');
    return poll(after, deps.pollMs ?? 25_000);
  }],
  ['POST', /^\/api\/next$/, ({ db, body }) => {
    sweepLinks(db);
    const at = now();
    if (body.interrupt && !shouldInterrupt(db, HERO_ID, at)) return { id: null };
    const opp = nextOpportunity(db, HERO_ID, at, body.scope === 'all' ? 'all' : 'brief');
    if (!opp) return { id: null };
    const { line, spoken } = phraseFull(opp);
    return { id: opp.id, type: opp.type, line, spoken, display: display(opp) };
  }],
  ['GET', /^\/api\/brief$/, ({ db }) => { sweepLinks(db); return briefList(db, HERO_ID, now()); }],
  ['GET', /^\/api\/facts$/, ({ db }) => getFacts(db, HERO_ID, now())],
  ['POST', /^\/api\/onboard\/lookup$/, ({ db, body }) => {
    // Simulated Paytm for Business lookup: any valid number "finds" the demo shop.
    const phone = normalisePhone(body.phone);
    if (!phone) throw new HttpError(400, 'bad_phone');
    return { profile: { ...getProfile(db, HERO_ID), phone } };
  }],
  ['GET', /^\/api\/merchant$/, ({ db }) => ({ profile: getProfile(db, HERO_ID) })],
  ['POST', /^\/api\/merchant$/, ({ db, body }) => {
    try { return { profile: saveProfile(db, HERO_ID, body, now()) }; }
    catch (e) { if (e instanceof ProfileError) throw new HttpError(400, e.message); throw e; }
  }],
  ['POST', /^\/api\/propose$/, ({ db, body }) => {
    if (body.kind !== 'udhaar_reminder' || !Number.isInteger(body.khataId)) throw new HttpError(400, 'kind udhaar_reminder and integer khataId required');
    const r = proposeReminder(db, HERO_ID, now(), body.khataId);
    if ('error' in r) throw new HttpError(r.error === 'khata_not_found' ? 404 : 409, r.error);
    const { line, spoken } = phraseFull(r.opp);
    return { opportunityId: r.opp.id, line, spoken, display: display(r.opp) };
  }],
  ['POST', /^\/api\/shelf\/readings$/, ({ db, body }) => {
    if (!Array.isArray(body.readings)) throw new HttpError(400, 'readings required');
    const readings = cleanReadings(db, HERO_ID, body.readings);
    if (!readings.length) throw new HttpError(422, UNREADABLE);
    return shelfFlow(db, HERO_ID, readings, now());
  }],
  ['POST', /^\/api\/opportunities\/(\d+)\/confirm$/, async ({ db, deps, params }) => {
    const r = await confirmOpportunity(db, Number(params[0]), deps, now());
    publish({ type: 'ledger' });
    return withSpoken(r);
  }],
  ['POST', /^\/api\/opportunities\/(\d+)\/decline$/, ({ db, params }) => { declineOpportunity(db, Number(params[0])); return withSpoken({ line: DECLINED }); }],
  ['POST', /^\/api\/converse$/, async ({ db, deps, body }) => {
    if (typeof body.text !== 'string' || !body.text.trim()) throw new HttpError(400, 'text required');
    return withSpoken(await converse(db, HERO_ID, body.text, now(), deps));
  }],
  ['GET', /^\/api\/pay\/([\w-]+)$/, ({ db, params }) => getPayLink(db, params[0], now()) ?? Promise.reject(new HttpError(404, 'unknown link'))],
  ['POST', /^\/api\/pay\/([\w-]+)$/, ({ db, params }) => {
    const r = payLink(db, params[0], now());
    if (!r) throw new HttpError(404, 'unknown link');
    if (r.expired) throw new HttpError(410, 'expired');
    if (!r.alreadyPaid) { announce(r.announcement); publish({ type: 'paid', ref: params[0], name: r.name, amount: r.amount }); publish({ type: 'ledger' }); }
    return { alreadyPaid: r.alreadyPaid };
  }],
  ['GET', /^\/api\/ledger$/, ({ db }) => getLedger(db, HERO_ID, now())],
  ['GET', /^\/api\/impact$/, ({ db, query }) => {
    const n = Math.round(Number(query.get('merchants') ?? 100000));
    if (!(n >= 1 && n <= 10_000_000)) throw new HttpError(400, 'merchants must be 1 to 1,00,00,000');
    return getImpact(db, HERO_ID, now(), n);
  }],
  ['POST', /^\/api\/control\/reset$/, ({ db, deps }) => {
    resetClock();
    seed(db, now(), { demoPhone: deps.demoPhone });
    publish({ type: 'reset' }); // open screens reload so no one acts on stale offers
    publish({ type: 'ledger' });
    return { ok: true };
  }],
  ['POST', /^\/api\/control\/advance$/, ({ body }) => { advance(Number(body.minutes) || 0); return { now: now().toISOString() }; }],
  ['POST', /^\/api\/control\/brief$/, () => { publish({ type: 'brief' }); return { ok: true }; }],
  // Photos are read by the phone's model now; the web stage fallback says so instead of failing silently.
  ['POST', /^\/api\/shelf$/, () => { throw new HttpError(503, SHELF_IN_APP); }],
  ['POST', /^\/api\/control\/shelf$/, ({ db }) => publishShelf(db, CANNED_SHELF)],
  ['POST', /^\/api\/control\/nudge$/, () => { publish({ type: 'nudge' }); return { ok: true }; }],
  ['GET', /^\/webhook\/whatsapp$/, ({ deps, query, res }) => {
    const ok = query.get('hub.mode') === 'subscribe' && !!deps.waVerifyToken && query.get('hub.verify_token') === deps.waVerifyToken;
    if (!ok) throw new HttpError(403, 'verify token mismatch');
    res.writeHead(200, { 'content-type': 'text/plain' });
    res.end(query.get('hub.challenge') ?? '');
    return undefined;
  }],
  ['POST', /^\/webhook\/whatsapp$/, ({ body }) => { handleWhatsApp(body); return { ok: true }; }],
  ['POST', /^\/api\/control\/regular$/, ({ db }) => {
    for (const text of recordCustomerPayment(db, HERO_ID, 180, hashPayer(`${HERO_ID}:loyal`), now())) announce(text);
    publish({ type: 'ledger' });
    return { ok: true };
  }],
  ['POST', /^\/api\/control\/returning$/, ({ db }) => {
    const t = db.prepare(
      `SELECT t.payer_hash FROM winback_targets t JOIN winback_campaigns c ON c.id = t.campaign_id
       WHERE c.merchant_id=? AND t.returned_at IS NULL ORDER BY c.id DESC LIMIT 1`,
    ).get(HERO_ID) as any;
    if (!t) throw new HttpError(409, 'Pehle win-back offer bhejiye');
    for (const text of recordCustomerPayment(db, HERO_ID, 340, t.payer_hash, now())) announce(text);
    publish({ type: 'ledger' });
    return { ok: true };
  }],
  ['POST', /^\/api\/control\/walkin$/, ({ db, body }) => {
    const amount = Math.round(Number(body.amount));
    if (!(amount > 0)) throw new HttpError(400, 'amount required');
    announce(recordWalkin(db, HERO_ID, amount, now()));
    return { ok: true };
  }],
];

async function readJson(req: IncomingMessage): Promise<any> {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 12_000_000) throw new HttpError(413, 'body too large');
  }
  if (!raw) return {};
  try { return JSON.parse(raw); } catch { throw new HttpError(400, 'invalid json'); }
}

async function serveStatic(path: string, res: ServerResponse) {
  const file = normalize(join(DIST, decodeURIComponent(path)));
  if (!file.startsWith(DIST)) throw new HttpError(404, 'not found');
  for (const candidate of [file, join(DIST, 'index.html')]) {
    try {
      const data = await readFile(candidate);
      res.writeHead(200, { 'content-type': TYPES[extname(candidate)] ?? 'application/octet-stream' });
      res.end(data);
      return;
    } catch { /* try next */ }
  }
  throw new HttpError(404, 'not found');
}

const send = (res: ServerResponse, status: number, data: unknown) => {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
};

export function createApp(db: DB, deps: AppDeps): http.Server {
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://x');
    const path = url.pathname;
    // Public tunnel: merchant and control APIs need the demo key; the customer pay API does not.
    const guarded = deps.demoKey && path.startsWith('/api/') && !path.startsWith('/api/pay/');
    if (guarded && (req.headers['x-demo-key'] ?? url.searchParams.get('key')) !== deps.demoKey) return send(res, 401, { error: 'demo key required' });
    try {
      for (const [method, re, handler] of routes) {
        const m = path.match(re);
        if (!m || method !== req.method) continue;
        const body = req.method === 'POST' ? await readJson(req) : {};
        const out = await handler({ db, deps, params: m.slice(1), query: url.searchParams, body, res });
        if (out !== undefined) send(res, 200, out);
        return;
      }
      if (req.method === 'GET' && !path.startsWith('/api/')) return await serveStatic(path, res);
      throw new HttpError(404, 'not found');
    } catch (e) {
      if (res.headersSent) return res.end();
      if (e instanceof NotProposedError) return send(res, 409, { error: 'not_proposed' });
      if (e instanceof HttpError) return send(res, e.status, { error: e.message });
      console.error(e);
      send(res, 500, { error: 'internal' });
    }
  });
  const sweep = setInterval(() => sweepLinks(db), 60_000);
  sweep.unref();
  server.on('close', () => clearInterval(sweep));
  return server;
}
