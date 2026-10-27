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
import { proposeReminder } from '../propose.ts';
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
  const db = openDb(file);
  const cols = (db.prepare('PRAGMA table_info(pay_links)').all() as any[]).map(c => c.name);
  db.close();
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

test('a proposed reminder for a recent debt keeps its re-offer after the link expires', async () => {
  // Verma ji's ₹120 is 3 days old: not overdue, so only a proposal (the phone's model) creates the task.
  const db = fresh();
  const khataId = (db.prepare("SELECT id FROM khata WHERE customer_name='Verma ji'").get() as any).id;
  const r = proposeReminder(db, HERO_ID, AT, khataId);
  assert.ok('opp' in r);
  const sent = await confirmOpportunity(db, r.opp.id, deps, AT);
  expireLinks(db, later(61));
  refreshOpportunities(db, HERO_ID, later(62));
  const again = getOpportunity(db, r.opp.id);
  assert.equal(again?.status, 'new');
  assert.equal(phraseFull(again!).line, 'Verma ji ka pehla link khatam ho gaya. ₹120 abhi baaki hai. Naya link bhej doon?');
  assert.ok(sent.payUrl);
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
