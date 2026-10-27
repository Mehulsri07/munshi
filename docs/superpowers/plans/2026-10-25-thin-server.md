# Thin Server Implementation Plan (plan d)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Node server stops calling any AI. It gives the phone what the phone's model needs, and its pay
links expire after an hour. The phone can reach it through a free Cloudflare Quick Tunnel, which can't carry
SSE, so the Android app long-polls for events instead.

**Architecture:**
- Rules and templates stay on the server. The Claude client, agent loop and image reading are deleted.
- New endpoints:
  - `/api/facts`: what the phone's model may talk about
  - `/api/propose`: the model suggests, the merchant decides
  - `/api/shelf/readings`: the model looked, the server checks
  - `/api/events/poll`: long-poll over an in-memory ring buffer
- Pay links gain `expires_at` / `expired_at`. A sweep announces each expiry once and re-opens the khata's
  task, which is then asked with a "naya link" line.
- `npm run tunnel` starts `cloudflared` and the server with `PUBLIC_URL` set.

**Tech Stack:** Node 22 (`--experimental-strip-types`), TypeScript, `node:sqlite`, `node:test`, React 18 (pay page), Kotlin + OkHttp + MockWebServer (Android long-poll), cloudflared.

**Spec:** `docs/superpowers/specs/2026-10-25-on-device-ai-design.md` §5 (server), §9 (failure rows for links, tunnel and long-poll), §10 (server tests).

## Global Constraints

- Work on branch `feat/on-device-ai`. Server commands run from the repo root; Gradle runs from `android/`
  with JBR 21.
- After every task: `npm test`, `npm run typecheck` and `npm run build` are green. Android tasks also need
  `./gradlew testDebugUnitTest`.
- No AI API anywhere in `server/`: no `@anthropic-ai/sdk`, no `ANTHROPIC_*`, no `messages.create`.
- Payer hashes never leave the server: no merchant- or phone-facing JSON selects `payments.payer_hash`.
- `PAY_LINK_TTL_MIN` defaults to **60**. Expiry uses the demo clock (`now()`), so `/control` "advance"
  expires links on stage.
- Long-poll: hold up to **25 s**; keep the last **200** events; no `after` returns `{events: [], seq}`
  immediately; a gap returns `{resync: true, seq}`.
- Everything Munshi says carries Devanagari for the phone's Hindi voice. New Roman lines get a `spoken.ts`
  entry.
- The pay page keeps "Demo payment — koi asli paisa nahi katega."
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## File Structure

```
package.json, package-lock.json        − @anthropic-ai/sdk; + "tunnel" script
README.md                              env table, Public URL and WhatsApp sections
server/
  main.ts                              no Claude; PAY_LINK_TTL_MIN
  actions.ts                           Deps without anthropic, + payLinkTtlMin; link expires_at; template params
  agent.ts                             classifyReply + converse only (no agent loop)
  phrase.ts                            templates only; udhaar "naya link" variant
  shelf.ts                             − analyzeShelf; + cleanReadings
  tools.ts                             DELETED (send_udhaar_reminder logic moves to propose.ts)
  facts.ts                             NEW getFacts, openKhata
  propose.ts                           NEW proposeReminder
  payments.ts                          expiry in getPayLink / payLink; expireLinks sweep
  detectors.ts                         udhaar payload gains relink: true after an expiry
  display.ts, spoken.ts, lines.ts      relink variants, SHELF_IN_APP, expiry announcement
  events.ts                            expired type; ring buffer; since(); poll()
  db.ts                                pay_links columns + migration
  whatsapp.ts                          − media fetch; template messages
  tunnel.ts                            NEW tunnelUrl() parser
  app.ts                               routes: facts, propose, shelf/readings, events/poll; shelf stub; sweep
  test/no-claude.test.ts, test/expiry.test.ts, test/phone-api.test.ts, test/poll.test.ts,
  test/whatsapp.test.ts, test/tunnel.test.ts (new); agent/phrase/growth/finale tests trimmed
scripts/tunnel.ts                      NEW: cloudflared + server
web/api.ts, web/Pay.tsx                expired link state
android/app/src/main/java/com/opuscube/munshi/
  api/Models.kt                        SoundboxEvent.Expired, Resync; PollResult, parsePoll
  events/EventStream.kt                long-poll instead of SSE
  conductor/Conductor.kt               Expired clears the QR; Resync refreshes
android/app/src/test/java/com/opuscube/munshi/events/EventStreamTest.kt, conductor/ConductorTest.kt
docs/superpowers/notes/2026-10-tunnel-rehearsal.md   Task 8 results
```

---

### Task 1: Remove Claude from the server

**Files:**
- Create: `server/test/no-claude.test.ts`
- Modify: `package.json`, `package-lock.json` (via npm), `server/main.ts`, `server/actions.ts:9-10`, `server/agent.ts`, `server/phrase.ts`, `server/shelf.ts`, `server/app.ts`, `server/whatsapp.ts`, `server/lines.ts`, `server/spoken.ts`, `README.md` (env table)
- Delete: `server/tools.ts`
- Modify tests: `server/test/agent.test.ts`, `server/test/phrase.test.ts`, `server/test/growth.test.ts`, `server/test/finale.test.ts`

**Interfaces:**
- Produces:
  - `phraseFull(opp: OppRow): Phrased` (now synchronous) and `template(opp)` unchanged
  - `type Deps = { sendWhatsApp: SendWhatsApp; publicUrl: string }`
  - `AppDeps = Deps & { demoKey?: string; demoPhone?: string; waVerifyToken?: string }`
  - `SHELF_IN_APP` in `lines.ts`
  - `POST /api/shelf` → 503 `{error: SHELF_IN_APP}`

- [ ] **Step 1: Write the failing test**

`server/test/no-claude.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

test('nothing on the server calls an AI API', () => {
  const dir = new URL('../', import.meta.url);
  for (const f of readdirSync(dir).filter(f => f.endsWith('.ts'))) {
    const src = readFileSync(new URL(f, dir), 'utf8');
    assert.ok(!/@anthropic-ai\/sdk|ANTHROPIC_|messages\.create/.test(src), f);
  }
  const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
  assert.equal(pkg.dependencies?.['@anthropic-ai/sdk'], undefined);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --experimental-strip-types --no-warnings --test server/test/no-claude.test.ts`
Expected: FAIL, `main.ts` (or the first file with a match) named in the assertion message.

- [ ] **Step 3: Remove the SDK and the Claude code paths**

Run: `npm uninstall @anthropic-ai/sdk`

`server/lines.ts`: append

```ts
export const SHELF_IN_APP = 'Shelf ki photo Munshi app se kheenchiye.';
```

`server/spoken.ts`:
- Change the import to `import { DECLINED, NOT_UNDERSTOOD, SHELF_IN_APP } from './lines.ts';`.
- Add to `exact`:

```ts
  [SHELF_IN_APP]: 'शेल्फ़ की फ़ोटो Munshi ऐप से खींचिए।',
```

`server/actions.ts`: delete line 9 (`AnthropicLike`), and change line 10 to

```ts
export type Deps = { sendWhatsApp: SendWhatsApp; publicUrl: string };
```

`server/phrase.ts`: replace the whole file with

```ts
import type { OppRow } from './opportunities.ts';
import { stockTemplate, groupTemplate, spikeTemplate } from './inventory.ts';
import { lapsedTemplate } from './customers.ts';
import { creditTemplate } from './credit.ts';
import { spokenFor } from './spoken.ts';

const templates: Record<string, (p: any) => string> = {
  udhaar_overdue: p => `${p.customerName} ka ₹${p.amount} udhaar ${p.days} din se baaki hai. Reminder bhej doon?`,
  stock_low: stockTemplate,
  group_buy_open: groupTemplate,
  local_demand_spike: spikeTemplate,
  lapsed_regulars: lapsedTemplate,
  cash_gap: creditTemplate,
};

export const template = (opp: OppRow): string => templates[opp.type](opp.payload);

export type Phrased = { line: string; spoken: string };

// The server only speaks templates; the phone's on-device model rewords them.
export const phraseFull = (opp: OppRow): Phrased => ({ line: template(opp), spoken: spokenFor(opp) });
```

`server/agent.ts`: replace the whole file with

```ts
import type { DB } from './db.ts';
import type { ActionResult, Deps } from './actions.ts';
import { confirmOpportunity, declineOpportunity, getProposed } from './opportunities.ts';
import { DECLINED, NOT_UNDERSTOOD } from './lines.ts';

// A reply is yes (or no) when every word is a yes-word or a polite filler and at least one
// is a real yes. "हाँ भेज दो", "ok ji", "mat bhejo" all count; "haan par kal" is for the phone's model.
const words = (s: string) => new Set(s.split(' '));
const YES_CORE = words('haan haa han ha hanji haanji yes ok okay theek thik zaroor bhej bhejo kar karo हाँ हां हा ओके ठीक ज़रूर जरूर भेज भेजो कर करो');
const YES_FILLER = words('ji जी do दो de दे dijiye दीजिए hai है please');
const NO_CORE = words('nahi nahin na no mat nako rehne नहीं नही ना मत रहने');
const NO_FILLER = words('ji जी chahiye चाहिए abhi अभी do दो bhej bhejo kar karo भेज भेजो कर करो hai है');

const allOf = (tokens: string[], core: Set<string>, filler: Set<string>) =>
  tokens.some(t => core.has(t)) && tokens.every(t => core.has(t) || filler.has(t));

export { DECLINED, NOT_UNDERSTOOD };

export function classifyReply(text: string): 'yes' | 'no' | 'other' {
  const tokens = text.normalize('NFC').toLowerCase().replace(/[\p{P}\p{S}]/gu, ' ').split(/\s+/).filter(Boolean);
  // A trailing "na" after other words is a softener ("bhej do na" = please send), not a no.
  if (tokens.length > 1 && ['na', 'ना'].includes(tokens[tokens.length - 1])) tokens.pop();
  if (!tokens.length) return 'other';
  if (allOf(tokens, NO_CORE, NO_FILLER)) return 'no';
  return allOf(tokens, YES_CORE, YES_FILLER) ? 'yes' : 'other';
}

type Reply = ActionResult & { opportunityId?: number };

// Yes or no on the open proposal. Anything else is answered on the phone (POST /api/propose for actions).
export async function converse(db: DB, merchantId: string, text: string, at: Date, deps: Deps): Promise<Reply> {
  const intent = classifyReply(text);
  const proposed = getProposed(db, merchantId);
  if (proposed && intent === 'yes') return { ...(await confirmOpportunity(db, proposed.id, deps, at)), opportunityId: proposed.id };
  if (proposed && intent === 'no') { declineOpportunity(db, proposed.id); return { line: DECLINED, opportunityId: proposed.id }; }
  return { line: NOT_UNDERSTOOD };
}
```

`server/shelf.ts`:
- Delete the imports `import type { AnthropicLike } from './actions.ts';` and `import { MODEL, template, withTimeout } from './phrase.ts';`. Add `import { template } from './phrase.ts';`.
- Delete `NoVisionError`, `UnreadablePhotoError` and the whole `analyzeShelf` function.

`server/whatsapp.ts`: delete `FetchMedia` and `makeFetchWhatsAppMedia` (lines 3–24).

`git rm server/tools.ts`

`server/app.ts`:
- Replace the imports of `phraseFull`, `shelf.ts` and `whatsapp.ts` with:

```ts
import { phraseFull } from './phrase.ts';
import { shelfFlow, type Reading } from './shelf.ts';
import { SHELF_IN_APP } from './lines.ts';
```

- Change `AppDeps` to `export type AppDeps = Deps & { demoKey?: string; demoPhone?: string; waVerifyToken?: string };`.
- Delete the `NO_VISION` and `UNREADABLE` constants.
- Replace `handleWhatsApp` with:

```ts
// A shelf photo sent on WhatsApp: photos are read on the phone now, so say where to take it.
function handleWhatsApp(body: any) {
  const msg = body?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
  if (msg?.type === 'image') announce(SHELF_IN_APP);
}
```

- In the `/api/next` route replace `const { line, spoken } = await phraseFull(opp, deps.anthropic);` with `const { line, spoken } = phraseFull(opp);`, and change its handler parameter list to `({ db, body })`.
- Replace the whole `/api/shelf` route with:

```ts
  // Photos are read by the phone's model now; the web stage fallback says so instead of failing silently.
  ['POST', /^\/api\/shelf$/, () => { throw new HttpError(503, SHELF_IN_APP); }],
```

- Replace the `POST /webhook/whatsapp` route body with `({ body }) => { handleWhatsApp(body); return { ok: true }; }` (Meta still gets a fast 200).

`server/main.ts`: replace the whole file with

```ts
import { openDb } from './db.ts';
import { seed } from './seed.ts';
import { now } from './clock.ts';
import { makeSendWhatsApp } from './whatsapp.ts';
import { createApp, type AppDeps } from './app.ts';

try { process.loadEnvFile(); } catch { /* .env is optional */ }
const env = process.env;

const deps: AppDeps = {
  sendWhatsApp: makeSendWhatsApp(env),
  publicUrl: (env.PUBLIC_URL ?? `http://localhost:${env.PORT ?? 3000}`).replace(/\/$/, ''),
  demoKey: env.DEMO_KEY,
  demoPhone: env.DEMO_CUSTOMER_PHONE,
  waVerifyToken: env.WA_VERIFY_TOKEN,
};

const db = openDb(env.MUNSHI_DB ?? 'munshi.db');
seed(db, now(), { demoPhone: deps.demoPhone });

const port = Number(env.PORT ?? 3000);
createApp(db, deps).listen(port, () => {
  console.log(`Munshi on http://localhost:${port}  (public: ${deps.publicUrl}, whatsapp: ${env.WA_TOKEN ? 'on' : 'off'})`);
});
```

`README.md` env table: delete the `ANTHROPIC_API_KEY` and `MUNSHI_MODEL` rows. Change the `WA_VERIFY_TOKEN`
row's "Does" cell to `verify token for the inbound webhook /webhook/whatsapp (a shelf photo sent there gets "Shelf ki photo Munshi app se kheenchiye.")`.

- [ ] **Step 4: Trim the tests that exercised Claude**

`server/test/agent.test.ts`:
- Change the imports to
  ```ts
  import { HERO_ID } from '../seed.ts';
  import { nextOpportunity } from '../opportunities.ts';
  import { classifyReply, converse } from '../agent.ts';
  import { AT, fresh, deps } from './helpers.ts';
  ```
- Delete the tests `phrase falls back to template without a client or on failure`, `phrase falls back to template when Claude is slower than 4 seconds`, `agent tool call proposes but never executes` and `no merchant-facing tool output contains payer_hash values` (Task 3 re-adds the payer-hash check for `/api/facts`).
- Rename `converse without Claude gives the fallback line` to `converse answers anything but yes or no with the fallback line`.

`server/test/phrase.test.ts`: replace the whole file with

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity } from '../opportunities.ts';
import { phraseFull, template } from '../phrase.ts';
import { spokenFor } from '../spoken.ts';
import { AT, fresh } from './helpers.ts';

test('the server speaks the template line and its Devanagari', () => {
  const opp = nextOpportunity(fresh(), HERO_ID, AT)!;
  assert.deepEqual(phraseFull(opp), { line: template(opp), spoken: spokenFor(opp) });
  assert.equal(phraseFull(opp).line, 'Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?');
});
```

`server/test/growth.test.ts`:
- Change the shelf import to `import { shelfFlow } from '../shelf.ts';`.
- Delete the tests `shelf vision needs Claude and keeps only known SKUs and levels` and `an unreadable shelf photo is reported as unreadable, never as all fine` (Task 3 covers the same filtering on `/api/shelf/readings`).

`server/test/finale.test.ts`:
- Delete the test `an unreadable shelf photo gets a clear line, not a server error`.
- In `control shelf beat announces what is low and offers the group order`, replace the `/api/shelf` assertion with:

```ts
    const photo = await fetch(`${base}/api/shelf`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: 'aGk=', mediaType: 'image/jpeg' }) });
    assert.equal(photo.status, 503);
    assert.equal((await photo.json()).error, 'Shelf ki photo Munshi app se kheenchiye.');
```

- [ ] **Step 5: Run everything**

Run: `npm test && npm run typecheck && npm run build`
Expected: all tests pass (fewer than 76: nine Claude tests went, one test came in), typecheck clean, build OK.
`grep -rn "anthropic" server package.json` prints nothing.

- [ ] **Step 6: Commit**

```bash
git add -A package.json package-lock.json README.md server
git commit -m "refactor(server): remove the Claude API; templates and rules only" -m "The phone's on-device model takes over rewording, questions and shelf photos (plan e)." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Pay links expire after an hour

**Files:**
- Create: `server/test/expiry.test.ts`
- Modify: `server/db.ts` (schema + `openDb` migration), `server/actions.ts` (Deps, insert), `server/payments.ts`, `server/detectors.ts:11-20`, `server/phrase.ts` (udhaar template), `server/spoken.ts` (udhaar + announcement), `server/display.ts` (udhaar), `server/events.ts` (type), `server/app.ts` (pay routes, sweep), `server/main.ts` (TTL), `web/api.ts`, `web/Pay.tsx`

**Interfaces:**
- Consumes: `phraseFull`, `Deps` (Task 1).
- Produces:
  - `Deps.payLinkTtlMin?: number` (default 60)
  - `getPayLink(db, ref, at: Date)` → `{ ref, merchantName, customerName, amount, status: 'pending' | 'paid' | 'expired', expiresAt: string | null } | null`
  - `payLink(db, ref, at)` → `{ alreadyPaid, expired, announcement, name, amount } | null`
  - `expireLinks(db, at): Array<{ ref: string; name: string; amount: number }>`
  - `SoundboxEvent` gains `{ type: 'expired'; ref: string; name: string; amount: number }`
  - Udhaar payload `relink: true` after an expiry
  - Announcement `"<name> ka pay link khatam ho gaya."`

- [ ] **Step 1: Write the failing tests**

`server/test/expiry.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../db.ts';
import { seed, HERO_ID } from '../seed.ts';
import { now, resetClock } from '../clock.ts';
import { nextOpportunity, confirmOpportunity, getOpportunity, proposeOpportunity } from '../opportunities.ts';
import { refreshOpportunities } from '../detectors.ts';
import { getPayLink, payLink, expireLinks } from '../payments.ts';
import { phraseFull } from '../phrase.ts';
import { display } from '../display.ts';
import { createApp } from '../app.ts';
import { onEvent } from '../events.ts';
import { AT, fresh, deps } from './helpers.ts';

const MIN = 60_000;
const later = (m: number) => new Date(AT.getTime() + m * MIN);

async function sharmaLink(db = fresh()) {
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await confirmOpportunity(db, opp.id, deps, AT);
  return { db, opp, ref: r.payUrl!.split('/').pop()! };
}

test('an old database file gains the expiry columns', () => {
  const file = join(mkdtempSync(join(tmpdir(), 'munshi-')), 'old.db');
  const old = new DatabaseSync(file);
  old.exec(`CREATE TABLE pay_links (ref TEXT PRIMARY KEY, merchant_id TEXT NOT NULL, khata_id INTEGER NOT NULL, action_id INTEGER NOT NULL,
    amount INTEGER NOT NULL, status TEXT NOT NULL CHECK (status IN ('pending','paid')), paid_at TEXT)`);
  old.close();
  const cols = (openDb(file).prepare('PRAGMA table_info(pay_links)').all() as any[]).map(c => c.name);
  assert.ok(cols.includes('expires_at') && cols.includes('expired_at'), cols.join(','));
});

test('a new link expires 60 minutes after it is made', async () => {
  const { db, ref } = await sharmaLink();
  assert.equal(getPayLink(db, ref, later(59))!.status, 'pending');
  assert.equal(getPayLink(db, ref, later(59))!.expiresAt, later(60).toISOString());
  assert.equal(getPayLink(db, ref, later(60))!.status, 'expired');
});

test('paying an expired link changes nothing', async () => {
  const { db, ref } = await sharmaLink();
  const r = payLink(db, ref, later(61))!;
  assert.equal(r.expired, true);
  assert.equal(r.alreadyPaid, false);
  assert.equal((db.prepare("SELECT status FROM khata WHERE customer_name='Sharma ji'").get() as any).status, 'open');
  assert.equal((db.prepare("SELECT COUNT(*) n FROM ledger WHERE kind='udhaar_recovered'").get() as any).n, 0);
});

test('the sweep reports each expiry once and re-offers the reminder with a fresh-link line', async () => {
  const { db, opp, ref } = await sharmaLink();
  assert.deepEqual(expireLinks(db, later(30)), []);
  assert.deepEqual(expireLinks(db, later(61)), [{ ref, name: 'Sharma ji', amount: 450 }]);
  assert.deepEqual(expireLinks(db, later(62)), []);
  assert.equal(getOpportunity(db, opp.id)!.status, 'new');
  // Propose it directly: which task is top by value at 10:02 depends on the demand-spike detector.
  refreshOpportunities(db, HERO_ID, later(62));
  const again = proposeOpportunity(db, HERO_ID, opp.id);
  assert.deepEqual(phraseFull(again), {
    line: 'Sharma ji ka pehla link khatam ho gaya. ₹450 abhi baaki hai. Naya link bhej doon?',
    spoken: 'Sharma ji का पहला लिंक खत्म हो गया। ₹450 अभी बाकी है। नया लिंक भेज दूँ?',
  });
  assert.deepEqual(display(again), { who: 'Sharma ji ka', amount: 450, rest: 'abhi baaki. Pehla link khatam ho gaya, naya bhej doon?' });
  const r = await confirmOpportunity(db, again.id, deps, later(62));
  assert.notEqual(r.payUrl!.split('/').pop(), ref);
});

test('a paid link never expires', async () => {
  const { db, ref } = await sharmaLink();
  payLink(db, ref, later(10));
  assert.deepEqual(expireLinks(db, later(120)), []);
  assert.equal(getPayLink(db, ref, later(120))!.status, 'paid');
});

test('the TTL comes from deps', async () => {
  const db = fresh();
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await confirmOpportunity(db, opp.id, { ...deps, payLinkTtlMin: 2 }, AT);
  assert.equal(getPayLink(db, r.payUrl!.split('/').pop()!, later(2))!.status, 'expired');
});

test('over HTTP: advancing the clock expires the link, the pay API says 410, and the Soundbox hears it', async () => {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body = {}) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const next = await (await post('/api/next')).json();
    const ref = (await (await post(`/api/opportunities/${next.id}/confirm`)).json()).payUrl.split('/').pop();
    await post('/api/control/advance', { minutes: 61 });
    assert.equal((await (await fetch(`${base}/api/pay/${ref}`)).json()).status, 'expired');
    const pay = await post(`/api/pay/${ref}`);
    assert.equal(pay.status, 410);
    assert.equal((await pay.json()).error, 'expired');
    await fetch(`${base}/api/brief`);   // brief and next run the sweep
    assert.deepEqual(events.find(e => e.type === 'expired'), { type: 'expired', ref, name: 'Sharma ji', amount: 450 });
    assert.deepEqual(events.find(e => e.type === 'announce'), { type: 'announce', text: 'Sharma ji ka pay link khatam ho gaya.', spoken: 'Sharma ji का पे लिंक खत्म हो गया।' });
    assert.ok(!events.some(e => e.type === 'paid'));
  } finally {
    off();
    resetClock();
    server.close();
  }
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/expiry.test.ts`
Expected: FAIL (`expireLinks` is not exported; `expiresAt` undefined).

- [ ] **Step 3: Schema and migration**

`server/db.ts`: in `SCHEMA`, change the `pay_links` table's last line to
`amount INTEGER NOT NULL, status TEXT NOT NULL CHECK (status IN ('pending','paid')), paid_at TEXT, expires_at TEXT, expired_at TEXT`
(keep the closing `);` as it is). Replace `openDb` with:

```ts
export function openDb(path: string): DB {
  const db = new DatabaseSync(path);
  db.exec(SCHEMA);
  // Files made before pay links could expire: add the columns (CREATE IF NOT EXISTS keeps old tables as they were).
  const cols = (db.prepare('PRAGMA table_info(pay_links)').all() as any[]).map(c => c.name);
  for (const c of ['expires_at', 'expired_at']) if (!cols.includes(c)) db.exec(`ALTER TABLE pay_links ADD COLUMN ${c} TEXT`);
  return db;
}
```

- [ ] **Step 4: Links get an expiry; payments respect it; the sweep**

`server/actions.ts`:
- Change `Deps` to `export type Deps = { sendWhatsApp: SendWhatsApp; publicUrl: string; payLinkTtlMin?: number };`.
- In `sendUdhaarReminder`, change the executor's parameters to `async (db, opp, actionId, deps, at)` and replace the INSERT with:

```ts
  const expires = new Date(at.getTime() + (deps.payLinkTtlMin ?? 60) * 60_000).toISOString();
  db.prepare("INSERT INTO pay_links (ref,merchant_id,khata_id,action_id,amount,status,expires_at) VALUES (?,?,?,?,?,'pending',?)")
    .run(ref, opp.merchantId, k.id, actionId, k.amount, expires);
```

`server/payments.ts`:
- Change the import line to `import { tx, type DB } from './db.ts';`.
- Change `LINK_SQL`'s column list to start `SELECT p.ref, p.amount, p.status, p.expires_at, p.action_id, …` (rest unchanged).
- Replace `getPayLink` and `payLink` with:

```ts
const expiredAt = (r: any, at: Date) => r.status === 'pending' && r.expires_at != null && r.expires_at <= at.toISOString();

export function getPayLink(db: DB, ref: string, at: Date) {
  const r = db.prepare(LINK_SQL).get(ref) as any;
  if (!r) return null;
  const status: 'pending' | 'paid' | 'expired' = expiredAt(r, at) ? 'expired' : r.status;
  return { ref: r.ref as string, merchantName: r.merchant_name as string, customerName: r.customer_name as string, amount: r.amount as number, status, expiresAt: (r.expires_at ?? null) as string | null };
}

export function payLink(db: DB, ref: string, at: Date): { alreadyPaid: boolean; expired: boolean; announcement: string; name: string; amount: number } | null {
  const r = db.prepare(LINK_SQL).get(ref) as any;
  if (!r) return null;
  const announcement = `${r.customer_name} ne ₹${r.amount} chuka diye`;
  const base = { announcement, name: r.customer_name as string, amount: r.amount as number };
  const ts = at.toISOString();
  db.exec('BEGIN');
  try {
    const flipped = db.prepare("UPDATE pay_links SET status='paid', paid_at=? WHERE ref=? AND status='pending' AND (expires_at IS NULL OR expires_at > ?)").run(ts, ref, ts);
    if (flipped.changes === 0) { db.exec('COMMIT'); return { ...base, alreadyPaid: r.status === 'paid', expired: r.status !== 'paid' }; }
    db.prepare("INSERT INTO payments (merchant_id,amount,ts,payer_hash,counterparty) VALUES (?,?,?,?,'customer')")
      .run(r.merchant_id, r.amount, ts, hashPayer(r.phone));
    db.prepare("UPDATE khata SET status='paid' WHERE id=?").run(r.khata_id);
    db.prepare("INSERT INTO ledger (merchant_id,action_id,kind,amount,label,ts) VALUES (?,?,'udhaar_recovered',?,?,?)")
      .run(r.merchant_id, r.action_id, r.amount, `${r.customer_name} ka udhaar wapas`, ts);
    db.exec('COMMIT');
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
  return { ...base, alreadyPaid: false, expired: false };
}

// Links that ran out since the last sweep: mark each once and put the khata's reminder back on the list.
export function expireLinks(db: DB, at: Date): Array<{ ref: string; name: string; amount: number }> {
  const ts = at.toISOString();
  const rows = db.prepare(
    `SELECT p.ref, p.merchant_id, p.khata_id, p.amount, k.customer_name FROM pay_links p JOIN khata k ON k.id = p.khata_id
     WHERE p.status='pending' AND p.expired_at IS NULL AND p.expires_at IS NOT NULL AND p.expires_at <= ? ORDER BY p.expires_at`,
  ).all(ts) as any[];
  return tx(db, () => rows.map(r => {
    db.prepare('UPDATE pay_links SET expired_at=? WHERE ref=?').run(ts, r.ref);
    db.prepare("UPDATE opportunities SET status='new' WHERE merchant_id=? AND type='udhaar_overdue' AND key=? AND status='done'")
      .run(r.merchant_id, `khata:${r.khata_id}`);
    return { ref: r.ref as string, name: r.customer_name as string, amount: r.amount as number };
  }));
}
```

- [ ] **Step 5: The re-offer says "naya link"**

`server/detectors.ts`, `udhaarOverdue`: replace the SELECT and the payload so the khata knows when its last link ran out:

```ts
export const udhaarOverdue: Detector = (db, merchantId, at) =>
  (db.prepare(
    `SELECT k.id, k.customer_name, k.amount, k.given_on,
       EXISTS (SELECT 1 FROM pay_links p WHERE p.khata_id = k.id AND p.status='pending' AND p.expired_at IS NOT NULL) AS relink
     FROM khata k WHERE k.merchant_id=? AND k.status='open'`,
  ).all(merchantId) as any[])
    .map(k => ({ k, days: Math.floor((at.getTime() - new Date(k.given_on).getTime()) / DAY) }))
    .filter(({ days }) => days > 7)
    .map(({ k, days }) => ({
      type: 'udhaar_overdue',
      key: `khata:${k.id}`,
      estValue: k.amount,
      payload: { khataId: k.id, customerName: k.customer_name, amount: k.amount, days, ...(k.relink ? { relink: true } : {}) },
    }));
```

`server/phrase.ts`, `udhaar_overdue` template:

```ts
  udhaar_overdue: p => p.relink
    ? `${p.customerName} ka pehla link khatam ho gaya. ₹${p.amount} abhi baaki hai. Naya link bhej doon?`
    : `${p.customerName} ka ₹${p.amount} udhaar ${p.days} din se baaki hai. Reminder bhej doon?`,
```

`server/spoken.ts`:
- In `proposals`, the `udhaar_overdue` entry:

```ts
  udhaar_overdue: p => p.relink
    ? `${p.customerName} का पहला लिंक खत्म हो गया। ₹${p.amount} अभी बाकी है। नया लिंक भेज दूँ?`
    : `${p.customerName} का ₹${p.amount} उधार ${p.days} दिन से बाकी है। रिमाइंडर भेज दूँ?`,
```

- Add to the `lines` array, after the `chuka diye` entry:

```ts
  [/^(.+) ka pay link khatam ho gaya\.$/, '$1 का पे लिंक खत्म हो गया।'],
```

`server/display.ts`, `udhaar_overdue`:

```ts
  udhaar_overdue: p => p.relink
    ? { who: `${p.customerName} ka`, amount: p.amount, rest: 'abhi baaki. Pehla link khatam ho gaya, naya bhej doon?' }
    : { who: `${p.customerName} ka`, amount: p.amount, rest: `udhaar ${p.days} din se baaki hai. Reminder bhej doon?` },
```

- [ ] **Step 6: Events and routes**

`server/events.ts`: add to the `SoundboxEvent` union

```ts
  | { type: 'expired'; ref: string; name: string; amount: number }
```

`server/app.ts`:
- Change the payments import to `import { getPayLink, payLink, recordWalkin, recordCustomerPayment, expireLinks } from './payments.ts';`.
- Below `publishShelf` add:

```ts
// Tell the Soundbox once per link that ran out; the reminder is back on its list with a "naya link" line.
function sweepLinks(db: DB) {
  for (const x of expireLinks(db, now())) {
    publish({ type: 'expired', ...x });
    announce(`${x.name} ka pay link khatam ho gaya.`);
  }
}
```

- At the start of the `/api/next` handler body add `sweepLinks(db);`. Replace the `/api/brief` route with
  `['GET', /^\/api\/brief$/, ({ db }) => { sweepLinks(db); return briefList(db, HERO_ID, now()); }],`.
- Replace the two pay routes with:

```ts
  ['GET', /^\/api\/pay\/([\w-]+)$/, ({ db, params }) => getPayLink(db, params[0], now()) ?? Promise.reject(new HttpError(404, 'unknown link'))],
  ['POST', /^\/api\/pay\/([\w-]+)$/, ({ db, params }) => {
    const r = payLink(db, params[0], now());
    if (!r) throw new HttpError(404, 'unknown link');
    if (r.expired) throw new HttpError(410, 'expired');
    if (!r.alreadyPaid) { announce(r.announcement); publish({ type: 'paid', ref: params[0], name: r.name, amount: r.amount }); publish({ type: 'ledger' }); }
    return { alreadyPaid: r.alreadyPaid };
  }],
```

- In `createApp`, replace `return http.createServer(async (req, res) => { … });` with the server held in a const plus a minute sweep:

```ts
  const server = http.createServer(async (req, res) => {
    // … the existing handler body, unchanged …
  });
  const sweep = setInterval(() => sweepLinks(db), 60_000);
  sweep.unref();
  server.on('close', () => clearInterval(sweep));
  return server;
```

`server/main.ts`: add to `deps` after `publicUrl`:

```ts
  payLinkTtlMin: Number(env.PAY_LINK_TTL_MIN) || 60,
```

- [ ] **Step 7: The pay page says the link has run out**

`web/api.ts`: change `PayLink` to

```ts
export type PayLink = { ref: string; merchantName: string; customerName: string; amount: number; status: 'pending' | 'paid' | 'expired'; expiresAt: string | null };
```

`web/Pay.tsx`:
- Change `type State` to add `'expired'`: `type State = 'loading' | 'ready' | 'paying' | 'paid' | 'already' | 'expired' | 'unknown' | 'error';`.
- Change the `ApiError` import: `import { api, rupees, ApiError, type PayLink } from './api.ts';`.
- In the effect: `.then(l => { setLink(l); setState(l.status === 'paid' ? 'already' : l.status === 'expired' ? 'expired' : 'ready'); })`.
- In `pay`'s catch: `} catch (e) { setState(e instanceof ApiError && e.status === 410 ? 'expired' : 'error'); }`.
- Above `if (state === 'unknown')` add:

```tsx
  if (state === 'expired') {
    return (
      <main className="paper">
        <div className="masthead"><div className="wrap"><h1>{link?.merchantName ?? 'Bhugtaan link'}</h1></div></div>
        <div className="wrap pay-body">
          <div className="result" data-tone="decline"><CrossIcon /><div><h2>Yeh link khatam ho gaya.</h2><p>{link?.merchantName ?? 'Dukaandaar'} se naya link maangiye. Paise nahi kate.</p></div></div>
          <p className="fine">Demo payment — koi asli paisa nahi katega.</p>
        </div>
      </main>
    );
  }
```

- [ ] **Step 8: Run everything**

Run: `npm test && npm run typecheck && npm run build`
Expected: all pass, including the 7 expiry tests. The existing `finale core loop over HTTP` still sees `{ alreadyPaid: false }` then `{ alreadyPaid: true }`.

- [ ] **Step 9: Commit**

```bash
git add server web/api.ts web/Pay.tsx
git commit -m "feat(server): pay links expire after an hour and the reminder comes back with a fresh link" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Endpoints for the phone's model

**Files:**
- Create: `server/facts.ts`, `server/propose.ts`, `server/test/phone-api.test.ts`
- Modify: `server/shelf.ts` (add `cleanReadings`), `server/app.ts` (three routes, `UNREADABLE`), `README.md` (API section)

**Interfaces:**
- Consumes: `phraseFull`, `display`, `shelfFlow`, `proposeOpportunity`, `refreshOpportunities`, `getLedger`, `template`.
- Produces:
  - `openKhata(db, merchantId, at): Array<{ khataId, customerName, amount, days }>`
  - `getFacts(db, merchantId, at): { shop: string; khata: Array<{ khataId, name, amount, days }>; tasks: Array<{ id, type, line, amount }>; week: { total }; inventory: Array<{ sku, name }> }`
  - `proposeReminder(db, merchantId, at, khataId): { error: 'khata_not_found' | 'already_sent' } | { opp: OppRow }`
  - `cleanReadings(db, merchantId, raw: unknown[]): Reading[]`
  - Routes:
    - `GET /api/facts`
    - `POST /api/propose {kind: 'udhaar_reminder', khataId}` → `{ opportunityId, line, spoken, display }`
    - `POST /api/shelf/readings {readings}` → shelfFlow result

- [ ] **Step 1: Write the failing tests**

`server/test/phone-api.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { openDb } from '../db.ts';
import { seed, HERO_ID } from '../seed.ts';
import { now } from '../clock.ts';
import { createApp } from '../app.ts';
import { getFacts } from '../facts.ts';
import { proposeReminder } from '../propose.ts';
import { cleanReadings } from '../shelf.ts';
import { getOpportunity, nextOpportunity, confirmOpportunity } from '../opportunities.ts';
import { AT, fresh, deps, sent } from './helpers.ts';

const khataId = (db: any, name: string) => (db.prepare('SELECT id FROM khata WHERE customer_name=?').get(name) as any).id;

test('facts give the phone names, amounts, tasks, the week and the shelf list, never payer hashes', () => {
  const db = fresh();
  const f = getFacts(db, HERO_ID, AT);
  assert.equal(f.shop, 'Ramesh Kirana');
  assert.deepEqual(f.khata.find(k => k.name === 'Sharma ji'), { khataId: khataId(db, 'Sharma ji'), name: 'Sharma ji', amount: 450, days: 12 });
  assert.ok(f.tasks.length > 0 && f.tasks.length <= 5);
  assert.deepEqual(f.tasks.map(t => t.amount), [...f.tasks.map(t => t.amount)].sort((a, b) => b - a));
  assert.equal(typeof f.week.total, 'number');
  assert.ok(f.inventory.some(i => i.sku === 'surf_1kg'));
  const json = JSON.stringify(f);
  assert.ok(!json.includes('payer_hash'));
  for (const { h } of db.prepare('SELECT DISTINCT payer_hash h FROM payments').all() as any[]) assert.ok(!json.includes(h));
});

test('propose makes a reminder proposal and never sends anything', () => {
  const db = fresh(); sent.length = 0;
  const r = proposeReminder(db, HERO_ID, AT, khataId(db, 'Pinky didi'));
  assert.ok('opp' in r);
  assert.equal(getOpportunity(db, r.opp.id)!.status, 'proposed');
  assert.equal(sent.length, 0);
  assert.equal((db.prepare('SELECT COUNT(*) n FROM actions').get() as any).n, 0);
});

test('propose refuses an unknown khata and one already sent', async () => {
  const db = fresh();
  assert.deepEqual(proposeReminder(db, HERO_ID, AT, 99999), { error: 'khata_not_found' });
  const opp = nextOpportunity(db, HERO_ID, AT)!;   // Sharma ji
  await confirmOpportunity(db, opp.id, deps, AT);
  assert.deepEqual(proposeReminder(db, HERO_ID, AT, khataId(db, 'Sharma ji')), { error: 'already_sent' });
});

test('readings keep only known SKUs and levels', () => {
  assert.deepEqual(
    cleanReadings(fresh(), HERO_ID, [{ sku: 'surf_1kg', level: 'empty' }, { sku: 'bogus', level: 'low' }, { sku: 'parle_g', level: 'meh' }, null, 'x']),
    [{ sku: 'surf_1kg', level: 'empty' }],
  );
});

test('over HTTP: facts, propose and shelf readings', async () => {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body: unknown) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    const facts = await (await fetch(`${base}/api/facts`)).json();
    const pinky = facts.khata.find((k: any) => k.name === 'Pinky didi');
    const p = await (await post('/api/propose', { kind: 'udhaar_reminder', khataId: pinky.khataId })).json();
    assert.match(p.line, /^Pinky didi ka ₹230 udhaar \d+ din se baaki hai\. Reminder bhej doon\?$/);
    assert.match(p.spoken, /^Pinky didi का ₹230 उधार/);
    assert.deepEqual(p.display.amount, 230);
    assert.equal(typeof p.opportunityId, 'number');
    assert.equal((await post('/api/propose', { kind: 'udhaar_reminder', khataId: 99999 })).status, 404);
    assert.equal((await post('/api/propose', { kind: 'order', khataId: pinky.khataId })).status, 400);

    const shelf = await (await post('/api/shelf/readings', { readings: [{ sku: 'surf_1kg', level: 'empty' }, { sku: 'parle_g', level: 'low' }] })).json();
    assert.equal(shelf.line, 'Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.');
    assert.match(shelf.next.line, /^Surf Excel 1kg ka group order/);
    const none = await post('/api/shelf/readings', { readings: [{ sku: 'bogus', level: 'low' }] });
    assert.equal(none.status, 422);
    assert.equal((await none.json()).error, 'Photo saaf nahi dikhi. Shelf ki photo dobara kheenchiye.');
    assert.equal((await post('/api/shelf/readings', { readings: 'x' })).status, 400);
  } finally { server.close(); }
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/phone-api.test.ts`
Expected: FAIL, `Cannot find module '../facts.ts'`.

- [ ] **Step 3: Implement**

`server/facts.ts`:

```ts
import type { DB } from './db.ts';
import { refreshOpportunities } from './detectors.ts';
import { getOpportunity } from './opportunities.ts';
import { getLedger } from './ledger.ts';
import { template } from './phrase.ts';

const DAY = 86_400_000;

export const openKhata = (db: DB, merchantId: string, at: Date) =>
  (db.prepare("SELECT id, customer_name, amount, given_on FROM khata WHERE merchant_id=? AND status='open' ORDER BY amount DESC").all(merchantId) as any[])
    .map(k => ({ khataId: k.id as number, customerName: k.customer_name as string, amount: k.amount as number, days: Math.floor((at.getTime() - new Date(k.given_on).getTime()) / DAY) }));

// Everything the phone's model may talk about, and nothing else. Merchant-facing: never select payer_hash.
export function getFacts(db: DB, merchantId: string, at: Date) {
  refreshOpportunities(db, merchantId, at);
  const shop = (db.prepare('SELECT name FROM merchants WHERE id=?').get(merchantId) as any).name as string;
  const khata = openKhata(db, merchantId, at).map(k => ({ khataId: k.khataId, name: k.customerName, amount: k.amount, days: k.days }));
  const tasks = (db.prepare("SELECT id FROM opportunities WHERE merchant_id=? AND status IN ('new','proposed') ORDER BY est_value DESC, id LIMIT 5").all(merchantId) as any[])
    .map(r => getOpportunity(db, r.id)!)
    .map(o => ({ id: o.id, type: o.type, line: template(o), amount: o.estValue }));
  const week = { total: getLedger(db, merchantId, at, 7).total };
  const inventory = db.prepare('SELECT sku, name FROM inventory WHERE merchant_id=? ORDER BY sku').all(merchantId) as Array<{ sku: string; name: string }>;
  return { shop, khata, tasks, week, inventory };
}
```

`server/propose.ts`:

```ts
import type { DB } from './db.ts';
import { proposeOpportunity, type OppRow } from './opportunities.ts';
import { openKhata } from './facts.ts';

// The phone's model can only suggest: this turns "Pinky didi ko yaad dilao" into a proposal that waits for "haan".
export function proposeReminder(db: DB, merchantId: string, at: Date, khataId: number): { error: 'khata_not_found' | 'already_sent' } | { opp: OppRow } {
  const k = openKhata(db, merchantId, at).find(k => k.khataId === khataId);
  if (!k) return { error: 'khata_not_found' };
  db.prepare("INSERT OR IGNORE INTO opportunities (merchant_id,type,key,payload,est_value,status,created_at) VALUES (?,'udhaar_overdue',?,?,?,'new',?)")
    .run(merchantId, `khata:${k.khataId}`, JSON.stringify(k), k.amount, at.toISOString());
  const row = db.prepare("SELECT id, status FROM opportunities WHERE merchant_id=? AND type='udhaar_overdue' AND key=?").get(merchantId, `khata:${k.khataId}`) as any;
  if (row.status === 'done' || row.status === 'accepted') return { error: 'already_sent' };
  return { opp: proposeOpportunity(db, merchantId, row.id) };
}
```

`server/shelf.ts`: add below `shelfFlow`:

```ts
// What the phone's model saw, trusted only for SKUs this shop stocks and the three known levels.
export function cleanReadings(db: DB, merchantId: string, raw: unknown[]): Reading[] {
  const skus = new Set(items(db, merchantId).map(i => i.sku));
  return raw
    .filter((r: any) => skus.has(r?.sku) && ['empty', 'low', 'ok'].includes(r?.level))
    .map((r: any) => ({ sku: r.sku, level: r.level as Level }));
}
```

`server/app.ts`:
- Imports: add `import { getFacts } from './facts.ts';` and `import { proposeReminder } from './propose.ts';`. Change the shelf import to `import { shelfFlow, cleanReadings, type Reading } from './shelf.ts';`.
- Below `CANNED_SHELF` add `const UNREADABLE = 'Photo saaf nahi dikhi. Shelf ki photo dobara kheenchiye.';`.
- Add these routes after the `/api/brief` route:

```ts
  ['GET', /^\/api\/facts$/, ({ db }) => getFacts(db, HERO_ID, now())],
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
```

`README.md`: in the app API section added by plan (a), add three bullets:

```markdown
- `GET /api/facts`: shop name, open khata (`khataId`, name, amount, days), up to five open tasks with their template line, the week's total and the shelf SKU list. Used by the phone's on-device model; never contains payer identities.
- `POST /api/propose` `{ "kind": "udhaar_reminder", "khataId": 3 }`: the model suggests a reminder; returns `{ opportunityId, line, spoken, display }` for Munshi to ask. 404 unknown khata, 409 already sent.
- `POST /api/shelf/readings` `{ "readings": [{ "sku": "surf_1kg", "level": "empty" }] }`: what the model saw on the shelf; unknown SKUs and levels are dropped, 422 if nothing usable is left.
```

- [ ] **Step 4: Run everything**

Run: `npm test && npm run typecheck && npm run build`
Expected: all pass, including the 5 phone-api tests.

- [ ] **Step 5: Commit**

```bash
git add server README.md
git commit -m "feat(server): facts, propose and shelf readings for the phone's on-device model" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Long-poll events

**Files:**
- Create: `server/test/poll.test.ts`
- Modify: `server/events.ts`, `server/app.ts` (route, `AppDeps.pollMs`)

**Interfaces:**
- Produces:
  - `since(after?: number): Polled`
  - `poll(after: number | undefined, waitMs: number): Promise<Polled>`
  - `type Polled = { events: Array<{ seq: number } & SoundboxEvent>; seq: number } | { resync: true; seq: number }`
  - `EVENT_BUFFER = 200`
  - `GET /api/events/poll[?after=N]`
  - `AppDeps.pollMs?: number` (default 25 000)

- [ ] **Step 1: Write the failing tests**

`server/test/poll.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { publish, since, poll, EVENT_BUFFER } from '../events.ts';
import { openDb } from '../db.ts';
import { seed } from '../seed.ts';
import { now } from '../clock.ts';
import { createApp } from '../app.ts';
import { deps } from './helpers.ts';

const seqNow = () => since().seq;

test('no cursor answers at once with the current position and no history', async () => {
  publish({ type: 'ledger' });
  assert.deepEqual(await poll(undefined, 5000), { events: [], seq: seqNow() });
});

test('a waiting poll returns as soon as something is published', async () => {
  const s = seqNow();
  setTimeout(() => publish({ type: 'nudge' }), 20);
  const t0 = Date.now();
  assert.deepEqual(await poll(s, 5000), { events: [{ seq: s + 1, type: 'nudge' }], seq: s + 1 });
  assert.ok(Date.now() - t0 < 1000);
});

test('a quiet poll gives up after the wait with nothing', async () => {
  const s = seqNow();
  assert.deepEqual(await poll(s, 50), { events: [], seq: s });
});

test('a client that fell behind the buffer, or is ahead of a restarted server, must resync', () => {
  const s = seqNow();
  for (let i = 0; i < EVENT_BUFFER + 5; i++) publish({ type: 'ledger' });
  assert.deepEqual(since(s), { resync: true, seq: seqNow() });
  assert.deepEqual(since(seqNow() + 10), { resync: true, seq: seqNow() });
});

test('reset clears history but still reaches a client that was up to date', () => {
  publish({ type: 'ledger' });
  const s = seqNow();
  publish({ type: 'reset' });
  assert.deepEqual(since(s), { events: [{ seq: s + 1, type: 'reset' }], seq: s + 1 });
  assert.deepEqual(since(s - 1), { resync: true, seq: s + 1 });
});

test('over HTTP the poll route uses the cursor and needs the demo key', async () => {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, { ...deps, demoKey: 'k3y', pollMs: 50 }).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  try {
    assert.equal((await fetch(`${base}/api/events/poll`)).status, 401);
    const first = await (await fetch(`${base}/api/events/poll?key=k3y`)).json();
    assert.deepEqual(first.events, []);
    await fetch(`${base}/api/control/nudge?key=k3y`, { method: 'POST' });
    const got = await (await fetch(`${base}/api/events/poll?key=k3y&after=${first.seq}`)).json();
    assert.deepEqual(got.events.map((e: any) => e.type), ['nudge']);
    assert.equal((await fetch(`${base}/api/events/poll?key=k3y&after=abc`)).status, 400);
  } finally { server.close(); }
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/poll.test.ts`
Expected: FAIL, `since` / `poll` not exported.

- [ ] **Step 3: Implement**

`server/events.ts`: replace `publish` and add the buffer, `since` and `poll` (keep `onEvent` and `subscribe`):

```ts
// Quick Tunnels drop SSE, so the phone long-polls. Recent events wait here, numbered, for it to collect.
export const EVENT_BUFFER = 200;
let seq = 0;
let recent: Array<{ seq: number; e: SoundboxEvent }> = [];
const waiting = new Set<() => void>();

export function publish(e: SoundboxEvent): void {
  seq++;
  if (e.type === 'reset') recent = [];   // nothing before a reset is worth replaying
  recent.push({ seq, e });
  if (recent.length > EVENT_BUFFER) recent.shift();
  for (const fn of listeners) fn(e);
  for (const wake of [...waiting]) wake();
}

export type Polled = { events: Array<{ seq: number } & SoundboxEvent>; seq: number } | { resync: true; seq: number };

// What a client missed since `after`. No cursor = start from now. A gap the buffer cannot fill
// (overrun, reset, server restart) = resync: the client re-fetches its state instead.
export function since(after?: number): Polled {
  if (after === undefined) return { events: [], seq };
  const oldest = recent[0]?.seq ?? seq + 1;
  if (after > seq || after < oldest - 1) return { resync: true, seq };
  return { events: recent.filter(r => r.seq > after).map(r => ({ seq: r.seq, ...r.e })), seq };
}

export function poll(after: number | undefined, waitMs: number): Promise<Polled> {
  const first = since(after);
  if (after === undefined || !('events' in first) || first.events.length) return Promise.resolve(first);
  return new Promise(resolve => {
    const wake = () => { clearTimeout(timer); waiting.delete(wake); resolve(since(after)); };
    const timer = setTimeout(wake, waitMs);
    waiting.add(wake);
  });
}
```

`server/app.ts`:
- Change the events import to `import { publish, subscribe, poll } from './events.ts';`.
- Change `AppDeps` to `export type AppDeps = Deps & { demoKey?: string; demoPhone?: string; waVerifyToken?: string; pollMs?: number };`.
- Add after the `/api/events` route:

```ts
  ['GET', /^\/api\/events\/poll$/, ({ deps, query }) => {
    const raw = query.get('after');
    const after = raw === null ? undefined : Number(raw);
    if (after !== undefined && !Number.isFinite(after)) throw new HttpError(400, 'after must be a number');
    return poll(after, deps.pollMs ?? 25_000);
  }],
```

- [ ] **Step 4: Run everything**

Run: `npm test && npm run typecheck && npm run build`
Expected: all pass, including the 6 poll tests.

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "feat(server): long-poll event feed for clients behind a Quick Tunnel" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: WhatsApp reminders as an approved template

**Files:**
- Create: `server/test/whatsapp.test.ts`
- Modify: `server/whatsapp.ts`, `server/actions.ts` (`sendUdhaarReminder`), `README.md` (env table, WhatsApp section)

**Interfaces:**
- Produces:
  - `type SendWhatsApp = (to: string, text: string, params?: string[]) => Promise<{ sent: boolean; error?: string }>`
  - `makeSendWhatsApp(env: { WA_TOKEN?; WA_PHONE_ID?; WA_GRAPH_VERSION?; WA_TEMPLATE?; WA_TEMPLATE_LANG? })`
  - Reminders pass `params = [name, "₹<amount>", payUrl]`

- [ ] **Step 1: Write the failing tests**

`server/test/whatsapp.test.ts`:

```ts
import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { makeSendWhatsApp } from '../whatsapp.ts';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity, confirmOpportunity } from '../opportunities.ts';
import { AT, fresh, deps } from './helpers.ts';

const realFetch = globalThis.fetch;
let bodies: any[] = [];
const stubFetch = () => {
  bodies = [];
  globalThis.fetch = (async (_url: any, init: any) => { bodies.push(JSON.parse(init.body)); return new Response('{}', { status: 200 }); }) as any;
};
afterEach(() => { globalThis.fetch = realFetch; });

const ENV = { WA_TOKEN: 't', WA_PHONE_ID: 'p' };
const PARAMS = ['Sharma ji', '₹450', 'https://demo.test/pay/ab12'];

test('not configured: nothing is sent', async () => {
  assert.deepEqual(await makeSendWhatsApp({})('+911', 'hi', PARAMS), { sent: false, error: 'not_configured' });
});

test('with WA_TEMPLATE the reminder goes as that template with name, amount and link', async () => {
  stubFetch();
  assert.deepEqual(await makeSendWhatsApp({ ...ENV, WA_TEMPLATE: 'udhaar_reminder' })('+91 98765 43210', 'free text', PARAMS), { sent: true });
  assert.deepEqual(bodies[0], {
    messaging_product: 'whatsapp', to: '919876543210', type: 'template',
    template: { name: 'udhaar_reminder', language: { code: 'en' }, components: [{ type: 'body', parameters: PARAMS.map(text => ({ type: 'text', text })) }] },
  });
});

test('without a template, or without params, it sends free-form text', async () => {
  stubFetch();
  await makeSendWhatsApp(ENV)('+911', 'free text', PARAMS);
  await makeSendWhatsApp({ ...ENV, WA_TEMPLATE: 'udhaar_reminder', WA_TEMPLATE_LANG: 'hi' })('+911', 'free text');
  assert.deepEqual(bodies.map(b => b.type), ['text', 'text']);
  assert.equal(bodies[0].text.body, 'free text');
});

test('the udhaar reminder hands the template its name, amount and pay link', async () => {
  const calls: unknown[][] = [];
  const db = fresh();
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await confirmOpportunity(db, opp.id, { ...deps, sendWhatsApp: async (...a) => { calls.push(a); return { sent: true }; } }, AT);
  assert.deepEqual(calls[0][2], ['Sharma ji', '₹450', r.payUrl]);
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/whatsapp.test.ts`
Expected: FAIL; the template test sees `type: 'text'`, and the reminder test sees `calls[0][2]` undefined.

- [ ] **Step 3: Implement**

`server/whatsapp.ts`: replace the whole file with

```ts
export type SendWhatsApp = (to: string, text: string, params?: string[]) => Promise<{ sent: boolean; error?: string }>;

// Meta WhatsApp Cloud API. A business-started message needs an approved template (WA_TEMPLATE, body
// parameters in order); without one, free-form text reaches only numbers that messaged us in the last
// 24 hours. Never throws: callers fall back to the QR.
export function makeSendWhatsApp(env: { WA_TOKEN?: string; WA_PHONE_ID?: string; WA_GRAPH_VERSION?: string; WA_TEMPLATE?: string; WA_TEMPLATE_LANG?: string }): SendWhatsApp {
  const { WA_TOKEN, WA_PHONE_ID, WA_GRAPH_VERSION = 'v23.0', WA_TEMPLATE, WA_TEMPLATE_LANG = 'en' } = env;
  return async (to, text, params) => {
    if (!WA_TOKEN || !WA_PHONE_ID) return { sent: false, error: 'not_configured' };
    const message = WA_TEMPLATE && params?.length
      ? { type: 'template', template: { name: WA_TEMPLATE, language: { code: WA_TEMPLATE_LANG }, components: [{ type: 'body', parameters: params.map(t => ({ type: 'text', text: t })) }] } }
      : { type: 'text', text: { body: text, preview_url: true } };
    try {
      const res = await fetch(`https://graph.facebook.com/${WA_GRAPH_VERSION}/${WA_PHONE_ID}/messages`, {
        method: 'POST',
        headers: { authorization: `Bearer ${WA_TOKEN}`, 'content-type': 'application/json' },
        body: JSON.stringify({ messaging_product: 'whatsapp', to: to.replace(/\D/g, ''), ...message }),
        signal: AbortSignal.timeout(5000),
      });
      return res.ok ? { sent: true } : { sent: false, error: `http_${res.status}: ${(await res.text()).slice(0, 200)}` };
    } catch (e) {
      return { sent: false, error: String(e) };
    }
  };
}
```

`server/actions.ts`, in `sendUdhaarReminder` replace the send line with:

```ts
  const { sent } = await deps.sendWhatsApp(k.phone, body, [k.customer_name, `₹${k.amount}`, payUrl]);
```

`README.md`:
- Add env rows after `WA_GRAPH_VERSION`:

```markdown
| `WA_TEMPLATE`, `WA_TEMPLATE_LANG` | send reminders as this approved utility template (body parameters: name, amount, pay link; language default `en`) | free-form text, which only reaches numbers that messaged the test number in the last 24 h |
| `PAY_LINK_TTL_MIN` | minutes before a pay link expires (default 60; set 2 to show expiry on stage) | 60 |
```

- In "WhatsApp test number", replace step 2 with: "2. Either create a utility template (e.g. `udhaar_reminder` with body `Namaste {{1}}, Ramesh Kirana se yaad dilaana tha: {{2}} baaki hain. Yahan chuka dein: {{3}}`) and set `WA_TEMPLATE`, or have the recipient message the test number first; that opens the 24-hour window for free-form text."

- [ ] **Step 4: Run everything**

Run: `npm test && npm run typecheck && npm run build`
Expected: all pass, including the 4 WhatsApp tests.

- [ ] **Step 5: Commit**

```bash
git add server README.md
git commit -m "feat(server): WhatsApp reminders as an approved template, free-form text as fallback" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `npm run tunnel`

**Files:**
- Create: `server/tunnel.ts`, `server/test/tunnel.test.ts`, `scripts/tunnel.ts`
- Modify: `package.json` (script), `README.md` (Public URL section)

**Interfaces:**
- Produces: `tunnelUrl(text: string): string | null`; the `npm run tunnel` command.

- [ ] **Step 1: Write the failing test**

`server/test/tunnel.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tunnelUrl } from '../tunnel.ts';

test('finds the Quick Tunnel address in cloudflared output', () => {
  const out = `2026-10-25T10:00:00Z INF Requesting new quick Tunnel on trycloudflare.com...
2026-10-25T10:00:02Z INF +--------------------------------------------------------------------------------------------+
2026-10-25T10:00:02Z INF |  https://quiet-river-1234-abc.trycloudflare.com                                             |`;
  assert.equal(tunnelUrl(out), 'https://quiet-river-1234-abc.trycloudflare.com');
  assert.equal(tunnelUrl('INF Starting metrics server on 127.0.0.1:20241/metrics'), null);
  assert.equal(tunnelUrl('see https://www.cloudflare.com/website-terms/'), null);
  assert.equal(tunnelUrl('ERR failed to request quick Tunnel: Post "https://api.trycloudflare.com/tunnel"'), null);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --experimental-strip-types --no-warnings --test server/test/tunnel.test.ts`
Expected: FAIL, `Cannot find module '../tunnel.ts'`.

- [ ] **Step 3: Implement**

`server/tunnel.ts`:

```ts
// cloudflared prints the Quick Tunnel address once, inside a box in its log. Its own API host
// (api.trycloudflare.com) shows up in error lines and is not ours.
export const tunnelUrl = (text: string): string | null => text.match(/https:\/\/(?!api\.)[a-z0-9-]+\.trycloudflare\.com/)?.[0] ?? null;
```

`scripts/tunnel.ts`:

```ts
// Free public address for the demo: a Cloudflare Quick Tunnel, then the server behind it with PUBLIC_URL set.
// Quick Tunnels change address on every start (re-pair the phone from /control) and do not carry SSE
// (the app long-polls). With TUNNEL_NAME and PUBLIC_URL set, runs that named tunnel instead.
import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { tunnelUrl } from '../server/tunnel.ts';

try { process.loadEnvFile(); } catch { /* .env is optional */ }
const env = process.env;
const port = env.PORT ?? '3000';

if (spawnSync('cloudflared', ['--version']).error) {
  console.error('cloudflared not found. Install it: winget install Cloudflare.cloudflared  (macOS: brew install cloudflared)');
  process.exit(1);
}
if (env.TUNNEL_NAME && !env.PUBLIC_URL) {
  console.error('TUNNEL_NAME needs PUBLIC_URL: the https hostname routed to that tunnel.');
  process.exit(1);
}

const demoKey = env.DEMO_KEY || randomBytes(9).toString('base64url');
const args = env.TUNNEL_NAME ? ['tunnel', 'run', env.TUNNEL_NAME] : ['tunnel', '--no-autoupdate', '--url', `http://localhost:${port}`];
const tunnel = spawn('cloudflared', args, { stdio: ['ignore', 'pipe', 'pipe'] });
let server: ChildProcess | undefined;

function startServer(publicUrl: string) {
  if (server) return;
  console.log(`\nPublic: ${publicUrl}\nControl (pair the phone here): ${publicUrl}/control?key=${demoKey}\n`);
  server = spawn(process.execPath, ['--experimental-strip-types', '--no-warnings', 'server/main.ts'], {
    stdio: 'inherit',
    env: { ...env, PUBLIC_URL: publicUrl, DEMO_KEY: demoKey, PORT: port },
  });
  server.on('exit', code => { tunnel.kill(); process.exit(code ?? 0); });
}

const scan = (chunk: Buffer) => { const url = tunnelUrl(chunk.toString()); if (url && !env.TUNNEL_NAME) startServer(url); };
tunnel.stdout.on('data', scan);
tunnel.stderr.on('data', scan);
tunnel.on('exit', code => { console.error(`cloudflared exited (${code})`); server?.kill(); process.exit(1); });
if (env.TUNNEL_NAME) startServer(env.PUBLIC_URL!.replace(/\/$/, ''));
process.on('SIGINT', () => { tunnel.kill(); server?.kill(); process.exit(0); });
```

`package.json` scripts: add after `"server"`:

```json
    "tunnel": "node --experimental-strip-types --no-warnings scripts/tunnel.ts",
```

`README.md`, replace the "Public URL" section body with:

````markdown
Phones must reach the server. One command opens a free Cloudflare Quick Tunnel and starts the server behind it:

```bash
npm run tunnel
```

It prints the public address and the `/control` link (with a generated `DEMO_KEY` unless you set one). Scan the
pairing QR on `/control` with the Munshi app. Quick Tunnels need no account but get a new address on every
start (pair again) and do not carry Server-Sent Events, so the app long-polls `/api/events/poll`; the web
pages' live updates work locally. For a fixed address, create a named tunnel on a domain in your Cloudflare
account and set `TUNNEL_NAME` and `PUBLIC_URL`. Install `cloudflared` with `winget install Cloudflare.cloudflared`.
````

Add an env row: ``| `TUNNEL_NAME` | `npm run tunnel` runs this named tunnel (needs `PUBLIC_URL`) | a Quick Tunnel with a random address |``.

- [ ] **Step 4: Run everything, then try the script**

Run: `npm test && npm run typecheck && npm run build`
Expected: all pass.

If `cloudflared` is installed, run `npm run tunnel`. It should print `Public: https://….trycloudflare.com`,
and `curl <that>/healthz` should return `{"ok":true}`. Stop it with Ctrl+C. If it is not installed, the
command prints the winget hint and exits 1; Task 8 covers the real run.

- [ ] **Step 5: Commit**

```bash
git add server/tunnel.ts server/test/tunnel.test.ts scripts/tunnel.ts package.json README.md
git commit -m "feat: npm run tunnel starts a Cloudflare Quick Tunnel and the server behind it" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Android long-poll and the expired event

**Files:**
- Modify: `android/app/src/main/java/com/opuscube/munshi/api/Models.kt:25-37`
- Modify: `android/app/src/main/java/com/opuscube/munshi/events/EventStream.kt`
- Modify: `android/app/src/main/java/com/opuscube/munshi/conductor/Conductor.kt` (`onEvent`)
- Test: `android/app/src/test/java/com/opuscube/munshi/events/EventStreamTest.kt`
- Test: `android/app/src/test/java/com/opuscube/munshi/conductor/ConductorTest.kt`

**Interfaces:**
- Consumes: `GET /api/events/poll` (Task 4); the `expired` event (Task 2).
- Produces:
  - `SoundboxEvent.Expired(ref, name?, amount?)` and `SoundboxEvent.Resync`
  - `data class PollResult(events, seq, resync)` and `fun parsePoll(data: String): PollResult?`
  - `EventStream` with the same constructor and `events()` / `live`, now long-polling

- [ ] **Step 1: Write the failing tests**

Replace the second test in `EventStreamTest.kt` (keep the backoff test) with:

```kotlin
    private fun json(body: String) = MockResponse().setHeader("content-type", "application/json").setBody(body)

    @Test fun `long-polls from the current position, skips unknown types, follows the cursor`() = runBlocking {
        val server = MockWebServer().apply { start() }
        server.enqueue(json("""{"events":[],"seq":4}"""))
        server.enqueue(json("""{"events":[{"seq":5,"type":"brief"},{"seq":6,"type":"mystery"}],"seq":6}"""))
        server.enqueue(json("""{"events":[{"seq":7,"type":"paid","ref":"r1","name":"Sharma ji","amount":450}],"seq":7}"""))
        val stream = EventStream(server.url("/").toString().trimEnd('/'), "k", OkHttpClient(), backoff = { 10L })
        val got = withTimeout(5000) { stream.events().take(2).toList() }
        assertEquals(listOf(SoundboxEvent.BriefCue, SoundboxEvent.Paid("r1", "Sharma ji", 450)), got)
        assertEquals("/api/events/poll?key=k", server.takeRequest().path)
        assertEquals("/api/events/poll?key=k&after=4", server.takeRequest().path)
        assertEquals("/api/events/poll?key=k&after=6", server.takeRequest().path)
        server.shutdown()
    }

    @Test fun `a failed poll backs off, and a resync is passed on`() = runBlocking {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(json("""{"events":[],"seq":2}"""))
        server.enqueue(json("""{"resync":true,"seq":9}"""))
        val stream = EventStream(server.url("/").toString().trimEnd('/'), "k", OkHttpClient(), backoff = { 10L })
        val got = withTimeout(5000) { stream.events().take(1).toList() }
        assertEquals(listOf(SoundboxEvent.Resync), got)
        assertEquals(true, stream.live.value)
        server.shutdown()
    }

    @Test fun `parsePoll reads events, cursor and resync`() {
        assertEquals(PollResult(listOf(SoundboxEvent.Expired("r1", "Sharma ji", 450)), 3, false),
            parsePoll("""{"events":[{"seq":3,"type":"expired","ref":"r1","name":"Sharma ji","amount":450}],"seq":3}"""))
        assertEquals(PollResult(emptyList(), 9, true), parsePoll("""{"resync":true,"seq":9}"""))
        assertEquals(null, parsePoll("not json"))
    }
```

Add `import com.opuscube.munshi.api.PollResult` and `import com.opuscube.munshi.api.parsePoll`; remove the now-unused `TimeUnit` import.

Add to `ConductorTest`:

```kotlin
    @Test fun `an expired link clears its QR, and a resync refreshes the brief and ledger`() = runTest {
        api.nexts += SHARMA
        api.confirmResult = { ActionResult("Sharma ji ko reminder bhej diya.", payUrl = "https://x/pay/ab12", payFor = PayFor("Sharma ji", 450)) }
        val c = conductor()
        c.openShop(); runCurrent()
        c.answer(Answer.Yes); runCurrent()
        c.onEvent(SoundboxEvent.Expired("other", "Pinky didi", 230)); runCurrent()
        assertNotNull(c.state.value.payQr)
        c.onEvent(SoundboxEvent.Expired("ab12", "Sharma ji", 450)); runCurrent()
        assertNull(c.state.value.payQr)
        api.calls.clear()
        c.onEvent(SoundboxEvent.Resync); runCurrent()
        assertEquals(listOf("brief", "ledger"), api.calls)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run (from `android/`): `./gradlew testDebugUnitTest --tests "com.opuscube.munshi.events.EventStreamTest" --tests "com.opuscube.munshi.conductor.ConductorTest" --console=plain`
Expected: compilation FAIL (`Unresolved reference: Expired`, `parsePoll`).

- [ ] **Step 3: Implement**

`api/Models.kt`, inside `sealed interface SoundboxEvent` add:

```kotlin
    @Serializable @SerialName("expired") data class Expired(val ref: String, val name: String? = null, val amount: Int? = null) : SoundboxEvent
    // Not sent by the server: EventStream emits it when the poll cursor fell off the server's buffer.
    @Serializable @SerialName("resync") data object Resync : SoundboxEvent
```

Below `parseEvent` add (with imports `kotlinx.serialization.json.jsonObject`, `jsonArray`, `jsonPrimitive`, `long`, `booleanOrNull`):

```kotlin
data class PollResult(val events: List<SoundboxEvent>, val seq: Long, val resync: Boolean)

// GET /api/events/poll: {"events":[{"seq":N,"type":…}],"seq":N} or {"resync":true,"seq":N}.
fun parsePoll(data: String): PollResult? = runCatching {
    val o = MunshiJson.parseToJsonElement(data).jsonObject
    PollResult(
        events = o["events"]?.jsonArray.orEmpty().mapNotNull { parseEvent(it.toString()) },
        seq = o.getValue("seq").jsonPrimitive.long,
        resync = o["resync"]?.jsonPrimitive?.booleanOrNull == true,
    )
}.getOrNull()
```

`events/EventStream.kt`: replace the whole file with

```kotlin
package com.opuscube.munshi.events

import com.opuscube.munshi.api.SoundboxEvent
import com.opuscube.munshi.api.parsePoll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume

fun backoffMs(attempt: Int): Long = minOf(15_000L, 1000L shl minOf(attempt, 4))

// The server's live feed by long-poll (Cloudflare Quick Tunnels do not carry SSE): announcements,
// payments, cues from /control. Retries forever with backoff; `live` drives the "Jud raha hai…" dot.
class EventStream(
    private val baseUrl: String,
    private val key: String,
    private val client: OkHttpClient,
    private val backoff: (Int) -> Long = ::backoffMs,
) {
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    fun events(): Flow<SoundboxEvent> = channelFlow {
        var after: Long? = null
        var attempt = 0
        val base = "$baseUrl/api/events/poll?key=${URLEncoder.encode(key, "UTF-8")}"
        while (isActive) {
            val r = get(base + (after?.let { "&after=$it" } ?: ""))?.let(::parsePoll)
            if (r == null) { _live.value = false; delay(backoff(attempt++)); continue }
            attempt = 0
            _live.value = true
            if (r.resync) send(SoundboxEvent.Resync)
            r.events.forEach { send(it) }
            after = r.seq
        }
    }

    private suspend fun get(url: String): String? = suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cont.resume(null) }
            override fun onResponse(call: Call, response: Response) {
                response.use { cont.resume(if (it.isSuccessful) it.body?.string() else null) }
            }
        })
    }
}
```

`conductor/Conductor.kt`, in `onEvent`'s `when`, add:

```kotlin
            // The link ran out: drop its QR. The server announces it and re-offers the reminder.
            is SoundboxEvent.Expired -> _state.update { s -> if (s.payQr?.ref == e.ref) s.copy(payQr = null) else s }
            SoundboxEvent.Resync -> scope.launch { refreshBrief(); refreshLedger() }
```

`MunshiApp.kt` needs no change: `Graph` already passes the no-read-timeout client to `EventStream`, and a
25 s poll fits it.

- [ ] **Step 4: Run all Android tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`, all tests pass (EventStreamTest 4, ConductorTest +1).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/opuscube/munshi android/app/src/test/java/com/opuscube/munshi
git commit -m "feat(android): long-poll the event feed so the app works through a Quick Tunnel" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Rehearsal through a real Quick Tunnel (checkpoint)

**Files:**
- Create: `docs/superpowers/notes/2026-10-tunnel-rehearsal.md`

**Interfaces:**
- Consumes: everything above; `cloudflared` installed (`winget install Cloudflare.cloudflared`); the emulator or the 8 GB phone; a second phone with a browser as the customer.

- [ ] **Step 1: Start the tunnel**

Run: `npm run tunnel` with `PAY_LINK_TTL_MIN=2` in `.env`, so expiry can be watched without advancing the clock.
Expected: `Public: https://….trycloudflare.com` and the `/control?key=…` link.

- [ ] **Step 2: Pair and run the loop**

```bash
./gradlew installDebug --console=plain
adb shell am start -n com.opuscube.munshi/.MainActivity --es url https://<public>.trycloudflare.com --es key <DEMO_KEY>
```

On the phone, check each step:
1. "Jud raha hai…" disappears within a few seconds.
2. **Dukaan kholo** speaks the brief; **Haan** on Sharma ji shows the QR.
3. On the second phone, open the QR link, which loads over the tunnel. Pay. Within about 2 s the Soundbox stamps "Chuka diya" and chimes.
4. Repeat for the next udhaar and **don't pay**. After about 2 minutes plus up to 1 minute of sweep, the
   banner says "<name> ka pay link khatam ho gaya.", the QR clears, and the customer page shows
   "Yeh link khatam ho gaya."
5. On `/control`, tap "Munshi, aur kuch?" (nudge). Munshi asks "<name> ka pehla link khatam ho gaya. … Naya link bhej doon?"
6. Stop `cloudflared` for 20 s, then start `npm run tunnel` again. The app shows "Jud raha hai…"; re-pair with the new address, and it recovers.

- [ ] **Step 3: Record and commit**

`docs/superpowers/notes/2026-10-tunnel-rehearsal.md`: date, device, each numbered check above with pass/fail
and timings (pay → stamp seconds), and any problem found. Fix any failure in the task that owns it before
moving on.

```bash
git add docs/superpowers/notes/2026-10-tunnel-rehearsal.md
git commit -m "test: Quick Tunnel rehearsal of pay, expiry and reconnect" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
