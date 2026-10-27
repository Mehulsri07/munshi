import { randomUUID } from 'node:crypto';
import { tx, type DB } from './db.ts';
import { hashPayer } from './seed.ts';

const LINK_SQL = `SELECT p.ref, p.amount, p.status, p.expires_at, p.action_id, p.merchant_id, m.name AS merchant_name, k.id AS khata_id, k.customer_name, k.phone
  FROM pay_links p JOIN merchants m ON m.id = p.merchant_id JOIN khata k ON k.id = p.khata_id WHERE p.ref = ?`;

const expiredAt = (r: any, at: Date) => r.status === 'pending' && r.expires_at != null && r.expires_at <= at.toISOString();

export function getPayLink(db: DB, ref: string, at: Date) {
  const r = db.prepare(LINK_SQL).get(ref) as any;
  if (!r) return null;
  const status: 'pending' | 'paid' | 'expired' = expiredAt(r, at) ? 'expired' : r.status;
  return { ref: r.ref as string, merchantName: r.merchant_name as string, customerName: r.customer_name as string, amount: r.amount as number, status, expiresAt: (r.expires_at ?? null) as string | null };
}

export function payLink(db: DB, ref: string, at: Date): { alreadyPaid: boolean; expired: boolean; announcement: string; name: string; amount: number } | null {
  const r = db.prepare(LINK_SQL).get(ref) as any;
  if (!r) return null;
  const announcement = `${r.customer_name} ne ₹${r.amount} chuka diye`;
  const base = { announcement, name: r.customer_name as string, amount: r.amount as number };
  const ts = at.toISOString();
  db.exec('BEGIN');
  try {
    const flipped = db.prepare("UPDATE pay_links SET status='paid', paid_at=? WHERE ref=? AND status='pending' AND (expires_at IS NULL OR expires_at > ?)").run(ts, ref, ts);
    if (flipped.changes === 0) { db.exec('COMMIT'); return { ...base, alreadyPaid: r.status === 'paid', expired: r.status !== 'paid' }; }
    db.prepare("INSERT INTO payments (merchant_id,amount,ts,payer_hash,counterparty) VALUES (?,?,?,?,'customer')")
      .run(r.merchant_id, r.amount, ts, hashPayer(r.phone));
    db.prepare("UPDATE khata SET status='paid' WHERE id=?").run(r.khata_id);
    db.prepare("INSERT INTO ledger (merchant_id,action_id,kind,amount,label,ts) VALUES (?,?,'udhaar_recovered',?,?,?)")
      .run(r.merchant_id, r.action_id, r.amount, `${r.customer_name} ka udhaar wapas`, ts);
    db.exec('COMMIT');
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
  return { ...base, alreadyPaid: false, expired: false };
}

// Links that ran out since the last sweep: mark each once and put the khata's reminder back on the list.
export function expireLinks(db: DB, at: Date): Array<{ ref: string; name: string; amount: number }> {
  const ts = at.toISOString();
  const rows = db.prepare(
    `SELECT p.ref, p.merchant_id, p.khata_id, p.amount, k.customer_name FROM pay_links p JOIN khata k ON k.id = p.khata_id
     WHERE p.status='pending' AND p.expired_at IS NULL AND p.expires_at IS NOT NULL AND p.expires_at <= ? ORDER BY p.expires_at`,
  ).all(ts) as any[];
  return tx(db, () => rows.map(r => {
    db.prepare('UPDATE pay_links SET expired_at=? WHERE ref=?').run(ts, r.ref);
    db.prepare("UPDATE opportunities SET status='new' WHERE merchant_id=? AND type='udhaar_overdue' AND key=? AND status='done'")
      .run(r.merchant_id, `khata:${r.khata_id}`);
    return { ref: r.ref as string, name: r.customer_name as string, amount: r.amount as number };
  }));
}

export function recordWalkin(db: DB, merchantId: string, amount: number, at: Date): string {
  return recordCustomerPayment(db, merchantId, amount, hashPayer(randomUUID()), at)[0];
}

// A counter payment and the rules that ride on it. Announcements never name the payer.
export function recordCustomerPayment(db: DB, merchantId: string, amount: number, payerHash: string, at: Date): string[] {
  const ts = at.toISOString();
  const said = [`₹${amount} prapt hue`];
  db.prepare("INSERT INTO payments (merchant_id,amount,ts,payer_hash,counterparty) VALUES (?,?,?,?,'customer')").run(merchantId, amount, ts, payerHash);

  const rule = db.prepare('SELECT visits_required, reward_amount FROM loyalty_rules WHERE merchant_id=?').get(merchantId) as any;
  if (rule) {
    const visits = (db.prepare("SELECT COUNT(*) n FROM payments WHERE merchant_id=? AND payer_hash=? AND counterparty='customer'").get(merchantId, payerHash) as any).n;
    if (visits % rule.visits_required === 0) said.push(`Dhanyavaad! Yeh grahak ki ${visits}vi khareed hai, agli baar ₹${rule.reward_amount} off.`);
  }

  const target = db.prepare(
    `SELECT t.rowid, c.offer, c.action_id FROM winback_targets t JOIN winback_campaigns c ON c.id = t.campaign_id
     WHERE c.merchant_id=? AND t.payer_hash=? AND t.returned_at IS NULL LIMIT 1`,
  ).get(merchantId, payerHash) as any;
  if (target) {
    db.prepare('UPDATE winback_targets SET returned_at=? WHERE rowid=?').run(ts, target.rowid);
    db.prepare("INSERT INTO ledger (merchant_id,action_id,kind,amount,label,ts) VALUES (?,?,'winback_sale',?,?,?)")
      .run(merchantId, target.action_id, amount, `Purana grahak lauta (₹${target.offer} offer)`, ts);
    said.push(`Ek purana grahak ₹${target.offer} offer se lauta! ₹${amount} ki khareed.`);
  }
  return said;
}
