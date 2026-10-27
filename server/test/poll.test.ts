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
