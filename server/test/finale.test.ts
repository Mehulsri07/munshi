import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { openDb } from '../db.ts';
import { seed } from '../seed.ts';
import { now } from '../clock.ts';
import { createApp } from '../app.ts';
import { onEvent } from '../events.ts';
import { deps } from './helpers.ts';

test('finale core loop over HTTP', async () => {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body = {}) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const heard: string[] = [];
  const off = onEvent(e => { if (e.type === 'announce') heard.push(e.text); });
  try {
    const next = await (await post('/api/next')).json();
    assert.match(next.line, /Sharma ji.*₹450/);
    const r = await (await post(`/api/opportunities/${next.id}/confirm`)).json();
    assert.equal((await post(`/api/opportunities/${next.id}/confirm`)).status, 409);
    const ref = r.payUrl.split('/').pop();
    assert.equal((await (await fetch(`${base}/api/pay/${ref}`)).json()).amount, 450);
    assert.deepEqual(await (await post(`/api/pay/${ref}`)).json(), { alreadyPaid: false });
    assert.deepEqual(await (await post(`/api/pay/${ref}`)).json(), { alreadyPaid: true });
    assert.deepEqual(heard, ['Sharma ji ne ₹450 chuka diye']);
    assert.equal((await (await fetch(`${base}/api/ledger`)).json()).total, 450);
    assert.equal((await fetch(`${base}/api/pay/nope`)).status, 404);
    assert.equal((await post('/api/converse', {})).status, 400);
  } finally {
    off();
    server.close();
  }
});

test('control shelf beat announces what is low and offers the group order', async () => {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const r = await (await fetch(`${base}/api/control/shelf`, { method: 'POST' })).json();
    assert.equal(r.line, 'Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.');
    assert.match(r.next.line, /^Surf Excel 1kg ka group order/);
    assert.deepEqual(events.map(e => e.type), ['shelf']);
    const photo = await fetch(`${base}/api/shelf`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: 'aGk=', mediaType: 'image/jpeg' }) });
    assert.equal(photo.status, 503);
    assert.equal((await photo.json()).error, 'Shelf ki photo Munshi app se kheenchiye.');
    assert.equal((await fetch(`${base}/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=nope&hub.challenge=42`)).status, 403);
  } finally {
    off();
    server.close();
  }
});

async function boot(extra: Record<string, unknown> = {}) {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, { ...deps, ...extra } as any).listen(0);
  await new Promise(r => server.once('listening', r));
  return { db, server, base: `http://localhost:${(server.address() as AddressInfo).port}` };
}

test('a malformed webhook image message does not crash the server', async () => {
  const { server, base } = await boot({ fetchMedia: async () => null });
  try {
    const body = { entry: [{ changes: [{ value: { messages: [{ type: 'image' }] } }] }] };
    assert.equal((await fetch(`${base}/webhook/whatsapp`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })).status, 200);
    await new Promise(r => setTimeout(r, 50));
    assert.equal((await fetch(`${base}/api/ledger`)).status, 200);
  } finally { server.close(); }
});

test('the demo key guards merchant and control routes but not the customer pay page', async () => {
  const { server, base } = await boot({ demoKey: 'k3y' });
  const post = (p: string, key?: string) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json', ...(key ? { 'x-demo-key': key } : {}) }, body: '{}' });
  try {
    assert.equal((await post('/api/control/reset')).status, 401);
    assert.equal((await post('/api/control/reset', 'wrong')).status, 401);
    assert.equal((await post('/api/control/reset', 'k3y')).status, 200);
    assert.equal((await fetch(`${base}/api/ledger`)).status, 401);
    assert.equal((await fetch(`${base}/api/events?key=nope`)).status, 401);
    assert.equal((await fetch(`${base}/api/pay/nope`)).status, 404);
    assert.equal((await post('/api/pay/nope')).status, 404);
  } finally { server.close(); }
});

test('paying publishes a paid event carrying the link ref', async () => {
  const { server, base } = await boot();
  const seen: any[] = [];
  const off = onEvent(e => seen.push(e));
  const post = (p: string) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' }).then(r => r.json());
  try {
    const n = await post('/api/next');
    const r = await post(`/api/opportunities/${n.id}/confirm`);
    const ref = r.payUrl.split('/').pop();
    await post(`/api/pay/${ref}`);
    assert.ok(seen.some(e => e.type === 'paid' && e.ref === ref));
  } finally { off(); server.close(); }
});

test('healthz answers without the demo key', async () => {
  const { server, base } = await boot({ demoKey: 'k3y' });
  try {
    const r = await fetch(`${base}/healthz`);
    assert.equal(r.status, 200);
    assert.deepEqual(await r.json(), { ok: true });
  } finally { server.close(); }
});

test('reset tells every open screen to reload', async () => {
  const { server, base } = await boot();
  const seen: any[] = [];
  const off = onEvent(e => seen.push(e.type));
  try {
    assert.equal((await fetch(`${base}/api/control/reset`, { method: 'POST' })).status, 200);
    assert.ok(seen.includes('reset'), seen.join(','));
  } finally { off(); server.close(); }
});
