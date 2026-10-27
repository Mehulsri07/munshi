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
import { expireLinks } from '../payments.ts';
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
    assert.equal(p.display.amount, 230);
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

test('facts keep a re-offered reminder even below the top five, with its Devanagari', async () => {
  const db = fresh();
  const r = proposeReminder(db, HERO_ID, AT, khataId(db, 'Verma ji'));
  assert.ok('opp' in r);
  await confirmOpportunity(db, r.opp.id, deps, AT);
  expireLinks(db, new Date(AT.getTime() + 61 * 60_000));
  const f = getFacts(db, HERO_ID, new Date(AT.getTime() + 62 * 60_000));
  const verma = f.tasks.find(t => t.id === r.opp.id);
  assert.equal(verma?.line, 'Verma ji ka pehla link khatam ho gaya. ₹120 abhi baaki hai. Naya link bhej doon?');
  assert.equal(verma?.spoken, 'Verma ji का पहला लिंक खत्म हो गया। ₹120 अभी बाकी है। नया लिंक भेज दूँ?');
  assert.ok(f.tasks.every(t => typeof t.spoken === 'string' && t.spoken.length > 0));
  assert.equal(new Set(f.tasks.map(t => t.id)).size, f.tasks.length);
});
