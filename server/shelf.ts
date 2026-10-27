import type { DB } from './db.ts';
import { refreshOpportunities } from './detectors.ts';
import { getOpportunity, proposeOpportunity } from './opportunities.ts';
import { template } from './phrase.ts';
import { display } from './display.ts';
import { spokenFor, toSpoken } from './spoken.ts';

export type Level = 'empty' | 'low' | 'ok';
export type Reading = { sku: string; level: Level };

const items = (db: DB, merchantId: string) =>
  db.prepare('SELECT sku, name, est_qty, reorder_point FROM inventory WHERE merchant_id=? ORDER BY sku').all(merchantId) as any[];

export function applyShelfReading(db: DB, merchantId: string, readings: Reading[], at: Date): { line: string; low: string[] } {
  const known = new Map(items(db, merchantId).map(i => [i.sku, i]));
  const low: string[] = [];
  for (const r of readings) {
    const item = known.get(r.sku);
    if (!item || r.level === 'ok') continue;
    const qty = r.level === 'empty' ? 0 : Math.min(item.est_qty, item.reorder_point - 1);
    db.prepare('UPDATE inventory SET est_qty=?, updated_at=? WHERE merchant_id=? AND sku=?').run(qty, at.toISOString(), merchantId, r.sku);
    low.push(r.sku);
  }
  const names = low.map(s => known.get(s).name);
  return { line: names.length ? `Shelf dekh li: ${names.join(' aur ')} kam hain.` : 'Shelf dekh li, sab theek hai.', low };
}

// Apply a reading, then put the best restock offer for what was seen in front of the merchant.
export function shelfFlow(db: DB, merchantId: string, readings: Reading[], at: Date) {
  const { line, low } = applyShelfReading(db, merchantId, readings, at);
  refreshOpportunities(db, merchantId, at);
  const best = low.length ? db.prepare(
    `SELECT id FROM opportunities WHERE merchant_id=? AND status IN ('new','proposed') AND type IN ('group_buy_open','stock_low')
     AND json_extract(payload,'$.sku') IN (${low.map(() => '?').join(',')}) ORDER BY est_value DESC LIMIT 1`,
  ).get(merchantId, ...low) as any : null;
  if (!best) return { line, spoken: toSpoken(line), next: null };
  const opp = getOpportunity(db, proposeOpportunity(db, merchantId, best.id).id)!;
  return { line, spoken: toSpoken(line), next: { id: opp.id, line: template(opp), spoken: spokenFor(opp), display: display(opp) } };
}

// What the phone's model saw, trusted only for SKUs this shop stocks and the three known levels.
export function cleanReadings(db: DB, merchantId: string, raw: unknown[]): Reading[] {
  const skus = new Set(items(db, merchantId).map(i => i.sku));
  return raw
    .filter((r: any) => skus.has(r?.sku) && ['empty', 'low', 'ok'].includes(r?.level))
    .map((r: any) => ({ sku: r.sku, level: r.level as Level }));
}
