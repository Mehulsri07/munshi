import type { OppRow } from './opportunities.ts';

// What the Soundbox screen sets in three parts: a small "who", a large ₹ figure, and the rest
// of the question in italic. The spoken and Roman lines stay whole; this is layout only.
export type Display = { who: string; amount: number; rest: string };

const rs = (n: number) => `₹${n.toLocaleString('en-IN')}`;
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

const displays: Record<string, (p: any) => Display> = {
  udhaar_overdue: p => p.relink
    ? { who: `${p.customerName} ka`, amount: p.amount, rest: 'abhi baaki. Pehla link khatam ho gaya, naya bhej doon?' }
    : { who: `${p.customerName} ka`, amount: p.amount, rest: `udhaar ${p.days} din se baaki hai. Reminder bhej doon?` },
  stock_low: p => ({ who: `${p.name} ka order`, amount: p.total, rest: `Sirf ${p.estQty} bache. ${p.distributor} se ${p.qty} mangwa doon?` }),
  group_buy_open: p => ({ who: `${p.name} group order`, amount: p.saving, rest: `bachenge: ${p.members} dukaanein, ${p.pct}% bachat. Jud jaaun?` }),
  local_demand_spike: p => ({ who: `Mohalle mein ${p.label} ki maang ${p.uplift}% upar`, amount: p.total, rest: `ka stock: ${p.name}, ${p.qty} pack. Mangwa doon?` }),
  lapsed_regulars: p => ({ who: `${p.count} pakke grahak 3 hafte se nahi aaye`, amount: p.offer, rest: 'ka offer bhejoon? Naam aapko nahi dikhega.' }),
  cash_gap: p => ({ who: 'Diwali bill ke liye loan', amount: p.principal, rest: `Asli kharcha ${rs(p.cost)}, roz ${rs(p.daily)} settlement se katega.` }),
};

const titles: Record<string, (p: any) => string> = {
  udhaar_overdue: p => `${p.customerName} ka udhaar`,
  stock_low: p => `${p.name} khatam ho raha`,
  group_buy_open: p => `${p.name} group order`,
  local_demand_spike: p => `${cap(p.label)} ki maang upar`,
  lapsed_regulars: p => `${p.count} purane grahak`,
  cash_gap: () => 'Diwali bill ka loan',
};

export const display = (opp: OppRow): Display => displays[opp.type](opp.payload);
export const briefTitle = (opp: OppRow): string => titles[opp.type](opp.payload);
