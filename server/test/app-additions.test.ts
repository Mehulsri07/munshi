import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { openDb } from '../db.ts';
import { seed } from '../seed.ts';
import { now } from '../clock.ts';
import { createApp } from '../app.ts';
import { onEvent } from '../events.ts';
import { deps } from './helpers.ts';

async function boot() {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body = {}) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json());
  const get = (p: string) => fetch(base + p).then(r => r.json());
  return { db, server, post, get };
}

// The HTTP tests seed from the real clock, and the demand-spike value moves with the time of
// day, so check order and sums rather than exact spike rupees.
test('brief lists the top three tasks with values and leaves them unproposed', async () => {
  const { db, server, get } = await boot();
  try {
    const b = await get('/api/brief');
    const amounts = b.items.map((i: any) => i.amount);
    assert.equal(b.items.length, 3);
    assert.deepEqual(b.items[0], { id: b.items[0].id, type: 'udhaar_overdue', title: 'Sharma ji ka udhaar', amount: 450 });
    assert.deepEqual(amounts, [...amounts].sort((x: number, y: number) => y - x));
    assert.equal(b.total, amounts.reduce((s: number, n: number) => s + n, 0));
    assert.ok(b.items.every((i: any) => ['udhaar_overdue', 'stock_low', 'group_buy_open', 'local_demand_spike'].includes(i.type)));
    assert.equal((db.prepare("SELECT COUNT(*) n FROM opportunities WHERE status='proposed'").get() as any).n, 0);
  } finally { server.close(); }
});

test('next carries display and Devanagari; confirm and decline carry spoken', async () => {
  const { server, post } = await boot();
  try {
    const next = await post('/api/next');
    assert.deepEqual(next.display, { who: 'Sharma ji ka', amount: 450, rest: 'udhaar 12 din se baaki hai. Reminder bhej doon?' });
    assert.equal(next.spoken, 'Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?');
    const r = await post(`/api/opportunities/${next.id}/confirm`);
    assert.equal(r.spoken, 'Sharma ji को रिमाइंडर भेज दिया।');
    const n2 = await post('/api/next');
    assert.equal((await post(`/api/opportunities/${n2.id}/decline`)).spoken, 'ठीक है, रहने देते हैं।');
  } finally { server.close(); }
});

test('paying a link publishes paid with name and amount, and a spoken announcement', async () => {
  const { server, post } = await boot();
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const next = await post('/api/next');
    const ref = (await post(`/api/opportunities/${next.id}/confirm`)).payUrl.split('/').pop();
    await post(`/api/pay/${ref}`);
    assert.deepEqual(events.find(e => e.type === 'paid'), { type: 'paid', ref, name: 'Sharma ji', amount: 450 });
    assert.deepEqual(events.find(e => e.type === 'announce'), { type: 'announce', text: 'Sharma ji ne ₹450 chuka diye', spoken: 'Sharma ji ने ₹450 चुका दिए' });
  } finally { off(); server.close(); }
});

test('shelf beat: next offer has display and spoken; walk-in announcement is spoken', async () => {
  const { server, post } = await boot();
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const r = await post('/api/control/shelf');
    assert.equal(r.next.display.who, 'Surf Excel 1kg group order');
    assert.match(r.next.spoken, /ग्रुप ऑर्डर/);
    assert.equal(r.spoken, 'शेल्फ़ देख ली: Surf Excel 1kg और Parle-G कम हैं।');
    await post('/api/control/walkin', { amount: 120 });
    assert.deepEqual(events.at(-1), { type: 'announce', text: '₹120 prapt hue', spoken: '₹120 प्राप्त हुए' });
  } finally { off(); server.close(); }
});
