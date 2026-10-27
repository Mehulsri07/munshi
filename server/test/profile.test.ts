import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../db.ts';
import { seed, HERO_ID } from '../seed.ts';
import { createApp } from '../app.ts';
import { getFacts } from '../facts.ts';
import { getProfile, saveProfile, normalisePhone, ProfileError } from '../profile.ts';
import { AT, fresh, deps, sent } from './helpers.ts';

const mine = { shopName: 'Gupta General Store', locality: 'Hazratganj, Lucknow', category: 'Kirana', ownerName: 'Suresh Gupta', addressAs: 'Suresh bhai', phone: '98765 43210' };

test('with nothing saved, the profile is the seeded shop', () => {
  assert.deepEqual(getProfile(fresh(), HERO_ID), { shopName: 'Ramesh Kirana', locality: 'Aliganj, Lucknow', category: 'kirana', ownerName: 'Ramesh Gupta', addressAs: 'Ramesh ji', phone: null });
});

test('phone numbers: ten digits, with or without +91, spaces or dashes', () => {
  assert.equal(normalisePhone('98765 43210'), '+919876543210');
  assert.equal(normalisePhone('+91-98765-43210'), '+919876543210');
  assert.equal(normalisePhone('919876543210'), '+919876543210');
  for (const bad of ['12345', '98765432101', 'abc', 42, null]) assert.equal(normalisePhone(bad), null);
});

test('a saved profile is read back, survives a demo reset, and names the shop in facts', () => {
  const db = fresh();
  assert.equal(saveProfile(db, HERO_ID, mine, AT).phone, '+919876543210');
  seed(db, AT); // what /api/control/reset does
  assert.equal(getProfile(db, HERO_ID).shopName, 'Gupta General Store');
  const f = getFacts(db, HERO_ID, AT);
  assert.equal(f.shop, 'Gupta General Store');
  assert.equal(f.addressAs, 'Suresh bhai');
});

test('a profile without a shop name or a name to call the merchant, or with a long field, is refused', () => {
  const db = fresh();
  for (const bad of [{ ...mine, shopName: ' ' }, { ...mine, addressAs: '' }, { ...mine, locality: 'x'.repeat(61) }, { ...mine, phone: '123' }])
    assert.throws(() => saveProfile(db, HERO_ID, bad, AT), ProfileError);
});

test('the phone API: lookup, read, save, and the WhatsApp text uses the saved shop name', async () => {
  const db = fresh(); sent.length = 0;
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body: unknown) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.equal((await post('/api/onboard/lookup', { phone: '12' })).status, 400);
    const found = await (await post('/api/onboard/lookup', { phone: '9876543210' })).json();
    assert.equal(found.profile.shopName, 'Ramesh Kirana');
    assert.equal(found.profile.phone, '+919876543210');
    assert.equal((await post('/api/merchant', { ...mine, shopName: '' })).status, 400);
    const saved = await (await post('/api/merchant', mine)).json();
    assert.equal(saved.profile.addressAs, 'Suresh bhai');
    assert.equal((await (await fetch(base + '/api/merchant')).json()).profile.shopName, 'Gupta General Store');
    const facts = await (await fetch(base + '/api/facts')).json();
    const sharma = facts.khata.find((k: any) => k.name === 'Sharma ji');
    const p = await (await post('/api/propose', { kind: 'udhaar_reminder', khataId: sharma.khataId })).json();
    await post(`/api/opportunities/${p.opportunityId}/confirm`, {});
    assert.match(sent.at(-1)![1], /Gupta General Store se yaad dilaana tha/);
  } finally { server.close(); }
});

test('a database file from before profiles opens and gains the table', () => {
  const dir = mkdtempSync(join(tmpdir(), 'munshi-'));
  const file = join(dir, 'old.db');
  const old = openDb(file);
  old.exec('DROP TABLE profile');
  old.close();
  const db = openDb(file);
  seed(db, AT);
  assert.equal(getProfile(db, HERO_ID).addressAs, 'Ramesh ji');
  db.close();
});

