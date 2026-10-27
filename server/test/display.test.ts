import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { refreshOpportunities } from '../detectors.ts';
import { getOpportunity, type OppRow } from '../opportunities.ts';
import { display, briefTitle } from '../display.ts';
import { AT, fresh } from './helpers.ts';

function all(): OppRow[] {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  return (db.prepare('SELECT id FROM opportunities ORDER BY id').all() as any[]).map(r => getOpportunity(db, r.id)!);
}
const byType = (type: string, pick: (p: any) => boolean = () => true) => all().find(o => o.type === type && pick(o.payload))!;

test('udhaar display splits who, amount and the question', () => {
  const d = display(byType('udhaar_overdue', p => p.customerName === 'Sharma ji'));
  assert.deepEqual(d, { who: 'Sharma ji ka', amount: 450, rest: 'udhaar 12 din se baaki hai. Reminder bhej doon?' });
});

test('every opportunity type has a display whose amount is a whole rupee figure from its payload', () => {
  const amountField: Record<string, string> = {
    udhaar_overdue: 'amount', stock_low: 'total', group_buy_open: 'saving',
    local_demand_spike: 'total', lapsed_regulars: 'offer', cash_gap: 'principal',
  };
  for (const o of all()) {
    const d = display(o);
    assert.equal(d.amount, o.payload[amountField[o.type]], o.type);
    assert.ok(Number.isInteger(d.amount), o.type);
    assert.ok(d.who.length > 0 && d.rest.length > 0, o.type);
    assert.ok(!/payer|hash/i.test(JSON.stringify(d)), o.type);
  }
});

test('cash gap display keeps the true cost and the daily deduction', () => {
  assert.deepEqual(display(byType('cash_gap')), {
    who: 'Diwali bill ke liye loan', amount: 50000, rest: 'Asli kharcha ₹1,240, roz ₹570 settlement se katega.',
  });
});

test('brief titles are short and name the task', () => {
  assert.equal(briefTitle(byType('udhaar_overdue', p => p.customerName === 'Sharma ji')), 'Sharma ji ka udhaar');
  assert.equal(briefTitle(byType('local_demand_spike', p => p.label === 'mithai')), 'Mithai ki maang upar');
  assert.equal(briefTitle(byType('group_buy_open')), 'Surf Excel 1kg group order');
  assert.equal(briefTitle(byType('stock_low')), 'Parle-G khatam ho raha');
});
