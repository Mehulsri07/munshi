import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity, confirmOpportunity } from '../opportunities.ts';
import { getPayLink, payLink, recordWalkin } from '../payments.ts';
import { getLedger } from '../ledger.ts';
import { AT, DAY, fresh, deps } from './helpers.ts';

async function remindSharma() {
  const db = fresh();
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const { payUrl } = await confirmOpportunity(db, opp.id, deps, AT);
  return { db, ref: payUrl!.split('/').pop()! };
}

test('paying a link announces, closes khata, and writes one ledger line', async () => {
  const { db, ref } = await remindSharma();
  assert.deepEqual(getPayLink(db, ref, AT), { ref, merchantName: 'Ramesh Kirana', customerName: 'Sharma ji', amount: 450, status: 'pending', expiresAt: new Date(AT.getTime() + 60 * 60_000).toISOString() });
  assert.deepEqual(payLink(db, ref, AT), { alreadyPaid: false, expired: false, announcement: 'Sharma ji ne ₹450 chuka diye', name: 'Sharma ji', amount: 450 });
  assert.equal((db.prepare("SELECT status FROM khata WHERE customer_name='Sharma ji'").get() as any).status, 'paid');
  const l = getLedger(db, HERO_ID, AT);
  assert.equal(l.total, 450);
  assert.deepEqual(l.items.map(i => [i.kind, i.label, i.tool]), [['udhaar_recovered', 'Sharma ji ka udhaar wapas', 'send_udhaar_reminder']]);
});

test('paying twice is idempotent', async () => {
  const { db, ref } = await remindSharma();
  payLink(db, ref, AT);
  assert.equal(payLink(db, ref, AT)!.alreadyPaid, true);
  assert.equal(getLedger(db, HERO_ID, AT).total, 450);
  assert.equal(getPayLink(db, ref, AT)!.status, 'paid');
});

test('unknown ref returns null', () => {
  assert.equal(payLink(fresh(), 'nope', AT), null);
  assert.equal(getPayLink(fresh(), 'nope', AT), null);
});

test('ledger excludes entries older than the window', async () => {
  const { db, ref } = await remindSharma();
  payLink(db, ref, AT);
  assert.equal(getLedger(db, HERO_ID, new Date(AT.getTime() + 8 * DAY)).total, 0);
});

test('walk-in payment announces amount', () => {
  assert.equal(recordWalkin(fresh(), HERO_ID, 120, AT), '₹120 prapt hue');
});
