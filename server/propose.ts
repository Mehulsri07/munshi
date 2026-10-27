import type { DB } from './db.ts';
import { proposeOpportunity, type OppRow } from './opportunities.ts';
import { openKhata } from './facts.ts';

// The phone's model can only suggest: this turns "Pinky didi ko yaad dilao" into a proposal that waits for "haan".
export function proposeReminder(db: DB, merchantId: string, at: Date, khataId: number): { error: 'khata_not_found' | 'already_sent' } | { opp: OppRow } {
  const k = openKhata(db, merchantId, at).find(k => k.khataId === khataId);
  if (!k) return { error: 'khata_not_found' };
  db.prepare("INSERT OR IGNORE INTO opportunities (merchant_id,type,key,payload,est_value,status,created_at) VALUES (?,'udhaar_overdue',?,?,?,'new',?)")
    .run(merchantId, `khata:${k.khataId}`, JSON.stringify(k), k.amount, at.toISOString());
  const row = db.prepare("SELECT id, status FROM opportunities WHERE merchant_id=? AND type='udhaar_overdue' AND key=?").get(merchantId, `khata:${k.khataId}`) as any;
  if (row.status === 'done' || row.status === 'accepted') return { error: 'already_sent' };
  return { opp: proposeOpportunity(db, merchantId, row.id) };
}
