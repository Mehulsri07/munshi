import type { OppRow } from './opportunities.ts';
import { stockTemplate, groupTemplate, spikeTemplate } from './inventory.ts';
import { lapsedTemplate } from './customers.ts';
import { creditTemplate } from './credit.ts';
import { spokenFor } from './spoken.ts';

const templates: Record<string, (p: any) => string> = {
  udhaar_overdue: p => p.relink
    ? `${p.customerName} ka pehla link khatam ho gaya. ₹${p.amount} abhi baaki hai. Naya link bhej doon?`
    : `${p.customerName} ka ₹${p.amount} udhaar ${p.days} din se baaki hai. Reminder bhej doon?`,
  stock_low: stockTemplate,
  group_buy_open: groupTemplate,
  local_demand_spike: spikeTemplate,
  lapsed_regulars: lapsedTemplate,
  cash_gap: creditTemplate,
};

export const template = (opp: OppRow): string => templates[opp.type](opp.payload);

export type Phrased = { line: string; spoken: string };

// The server only speaks templates; the phone's on-device model rewords them.
export const phraseFull = (opp: OppRow): Phrased => ({ line: template(opp), spoken: spokenFor(opp) });
