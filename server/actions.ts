import { randomUUID } from 'node:crypto';
import type { DB } from './db.ts';
import type { OppRow } from './opportunities.ts';
import type { SendWhatsApp } from './whatsapp.ts';
import { placeOrder, joinGroupBuy } from './inventory.ts';
import { sendWinbackOffer } from './customers.ts';
import { acceptLoan } from './credit.ts';
import { getProfile } from './profile.ts';

export type Deps = { sendWhatsApp: SendWhatsApp; publicUrl: string; payLinkTtlMin?: number };
export type ActionResult = { line: string; payUrl?: string; payFor?: { name: string; amount: number } };
export type Executor = (db: DB, opp: OppRow, actionId: number, deps: Deps, at: Date) => Promise<ActionResult>;

const sendUdhaarReminder: Executor = async (db, opp, actionId, deps, at) => {
  const k = db.prepare('SELECT * FROM khata WHERE id=?').get(opp.payload.khataId) as any;
  const ref = randomUUID().replace(/-/g, '').slice(0, 8);
  const expires = new Date(at.getTime() + (deps.payLinkTtlMin ?? 60) * 60_000).toISOString();
  db.prepare("INSERT INTO pay_links (ref,merchant_id,khata_id,action_id,amount,status,expires_at) VALUES (?,?,?,?,?,'pending',?)")
    .run(ref, opp.merchantId, k.id, actionId, k.amount, expires);
  const payUrl = `${deps.publicUrl}/pay/${ref}`;
  const shop = getProfile(db, opp.merchantId).shopName;
  const body = `Namaste ${k.customer_name}, ${shop} se yaad dilaana tha: ₹${k.amount} baaki hain. Yahan aasaani se chuka dein: ${payUrl} (Munshi AI dwara bheja gaya)`;
  const { sent } = await deps.sendWhatsApp(k.phone, body, [k.customer_name, `₹${k.amount}`, payUrl]);
  return {
    line: sent ? `${k.customer_name} ko reminder bhej diya.` : `WhatsApp nahi gaya. Yeh QR ${k.customer_name} ko dikha dijiye.`,
    payUrl,
    payFor: { name: k.customer_name, amount: k.amount },
  };
};

// Opportunity type → [tool name, executor]. Only confirmOpportunity calls these.
export const executors: Record<string, [string, Executor]> = {
  udhaar_overdue: ['send_udhaar_reminder', sendUdhaarReminder],
  stock_low: ['place_order', placeOrder],
  group_buy_open: ['join_group_buy', joinGroupBuy],
  local_demand_spike: ['place_order', placeOrder],
  lapsed_regulars: ['send_winback_offer', sendWinbackOffer],
  cash_gap: ['accept_loan', acceptLoan],
};
