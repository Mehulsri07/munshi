import type { DB } from './db.ts';
import { refreshOpportunities } from './detectors.ts';
import { getOpportunity } from './opportunities.ts';
import { getLedger } from './ledger.ts';
import { template } from './phrase.ts';
import { spokenFor } from './spoken.ts';
import { getProfile } from './profile.ts';

const DAY = 86_400_000;

export const openKhata = (db: DB, merchantId: string, at: Date) =>
  (db.prepare("SELECT id, customer_name, amount, given_on FROM khata WHERE merchant_id=? AND status='open' ORDER BY amount DESC").all(merchantId) as any[])
    .map(k => ({ khataId: k.id as number, customerName: k.customer_name as string, amount: k.amount as number, days: Math.floor((at.getTime() - new Date(k.given_on).getTime()) / DAY) }));

// Everything the phone's model may talk about, and nothing else. Merchant-facing: never select payer_hash.
export function getFacts(db: DB, merchantId: string, at: Date) {
  refreshOpportunities(db, merchantId, at);
  const profile = getProfile(db, merchantId);
  const shop = profile.shopName;
  const khata = openKhata(db, merchantId, at).map(k => ({ khataId: k.khataId, name: k.customerName, amount: k.amount, days: k.days }));
  // The top five by value, plus every reminder whose link ran out: the model should never forget those.
  const top = db.prepare("SELECT id FROM opportunities WHERE merchant_id=? AND status IN ('new','proposed') ORDER BY est_value DESC, id LIMIT 5").all(merchantId) as any[];
  const relink = db.prepare("SELECT id FROM opportunities WHERE merchant_id=? AND status IN ('new','proposed') AND type='udhaar_overdue' AND json_extract(payload,'$.relink')=1").all(merchantId) as any[];
  const tasks = [...new Set([...top, ...relink].map(r => r.id as number))]
    .map(id => getOpportunity(db, id)!)
    .map(o => ({ id: o.id, type: o.type, line: template(o), spoken: spokenFor(o), amount: o.estValue }));
  const week = { total: getLedger(db, merchantId, at, 7).total };
  const inventory = db.prepare('SELECT sku, name FROM inventory WHERE merchant_id=? ORDER BY sku').all(merchantId) as Array<{ sku: string; name: string }>;
  return { shop, addressAs: profile.addressAs, khata, tasks, week, inventory };
}
