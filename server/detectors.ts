import type { DB } from './db.ts';
import { stockLow, groupBuyOpen, localDemandSpike } from './inventory.ts';
import { lapsedRegulars } from './customers.ts';
import { cashGap } from './credit.ts';

export type Opportunity = { type: string; key: string; estValue: number; payload: Record<string, any> };
export type Detector = (db: DB, merchantId: string, at: Date) => Opportunity[];

const DAY = 86_400_000;

export const udhaarOverdue: Detector = (db, merchantId, at) =>
  (db.prepare(
    `SELECT k.id, k.customer_name, k.amount, k.given_on,
       EXISTS (SELECT 1 FROM pay_links p WHERE p.khata_id = k.id AND p.status='pending' AND p.expired_at IS NOT NULL) AS relink
     FROM khata k WHERE k.merchant_id=? AND k.status='open'`,
  ).all(merchantId) as any[])
    .map(k => ({ k, days: Math.floor((at.getTime() - new Date(k.given_on).getTime()) / DAY) }))
    // Overdue after a week; and once a reminder's link ran out unpaid, still owed whatever its age.
    .filter(({ k, days }) => days > 7 || k.relink)
    .map(({ k, days }) => ({
      type: 'udhaar_overdue',
      key: `khata:${k.id}`,
      estValue: k.amount,
      payload: { khataId: k.id, customerName: k.customer_name, amount: k.amount, days, ...(k.relink ? { relink: true } : {}) },
    }));

export const detectors: Detector[] = [udhaarOverdue, stockLow, groupBuyOpen, localDemandSpike, lapsedRegulars, cashGap];

// Upserts what the detectors see now. Open offers get fresh numbers; unasked offers that
// no longer hold are dropped. Proposed ones stay: the merchant is mid-decision.
export function refreshOpportunities(db: DB, merchantId: string, at: Date): void {
  const upsert = db.prepare(
    `INSERT INTO opportunities (merchant_id,type,key,payload,est_value,status,created_at) VALUES (?,?,?,?,?,'new',?)
     ON CONFLICT (merchant_id,type,key) DO UPDATE SET payload=excluded.payload, est_value=excluded.est_value
     WHERE status IN ('new','proposed')`,
  );
  const live = new Set<string>();
  for (const detect of detectors) {
    for (const o of detect(db, merchantId, at)) {
      upsert.run(merchantId, o.type, o.key, JSON.stringify(o.payload), o.estValue, at.toISOString());
      live.add(`${o.type}|${o.key}`);
    }
  }
  const drop = db.prepare('DELETE FROM opportunities WHERE id=?');
  for (const r of db.prepare("SELECT id, type, key FROM opportunities WHERE merchant_id=? AND status='new'").all(merchantId) as any[]) {
    if (!live.has(`${r.type}|${r.key}`)) drop.run(r.id);
  }
}
