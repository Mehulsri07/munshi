export type ActionResult = { line: string; payUrl?: string; payFor?: { name: string; amount: number }; opportunityId?: number };
export type Next = { id: number | null; type?: string; line?: string };
export type Proposal = { id: number; line: string };
export type ShelfResult = { line: string; next: Proposal | null };
export type PayLink = { ref: string; merchantName: string; customerName: string; amount: number; status: 'pending' | 'paid' | 'expired'; expiresAt: string | null };
export type Ledger = {
  total: number;
  items: Array<{ kind: string; amount: number; label: string; ts: string; tool: string }>;
  loan: { principal: number; totalCost: number; dailyDeduction: number; tenureDays: number } | null;
};
export type Impact = {
  week: { recovered: number; groupSavings: number; winbackSales: number; loanGmv: number; offersDelivered: number; actions: number };
  perMerchant: number;
  projected: { merchants: number; weeklyIncome: number; loanGmv: number; consumerReach: number };
};
export type SoundboxEvent =
  | { type: 'announce'; text: string }
  | { type: 'brief' }
  | { type: 'nudge' }
  | { type: 'ledger' }
  | { type: 'paid'; ref: string }
  | { type: 'reset' }
  | ({ type: 'shelf' } & ShelfResult);

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

// Demo key from ?key= (stored once) guards the merchant and control API on a public tunnel.
const KEY = (() => { try { const k = new URLSearchParams(location.search).get('key'); if (k) localStorage.setItem('munshi-key', k); return localStorage.getItem('munshi-key') ?? ''; } catch { return ''; } })();
export const demoKey = KEY;

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: { 'x-demo-key': KEY, ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(res.status, data.error ?? res.statusText);
  return data as T;
}

export const api = {
  next: (opts: { interrupt?: boolean; scope?: 'brief' | 'all' } = {}) => call<Next>('POST', '/api/next', opts),
  shelf: (image: string, mediaType: string) => call<ShelfResult>('POST', '/api/shelf', { image, mediaType }),
  impact: (merchants: number) => call<Impact>('GET', `/api/impact?merchants=${merchants}`),
  confirm: (id: number) => call<ActionResult>('POST', `/api/opportunities/${id}/confirm`, {}),
  decline: (id: number) => call<ActionResult>('POST', `/api/opportunities/${id}/decline`, {}),
  converse: (text: string) => call<ActionResult>('POST', '/api/converse', { text }),
  getPay: (ref: string) => call<PayLink>('GET', `/api/pay/${ref}`),
  pay: (ref: string) => call<{ alreadyPaid: boolean }>('POST', `/api/pay/${ref}`, {}),
  ledger: () => call<Ledger>('GET', '/api/ledger'),
  control: (action: string, body: unknown = {}) => call<unknown>('POST', `/api/control/${action}`, body),
};

// EventSource reconnects on its own; onStatus reports whether the stream is live.
export function onEvents(fn: (e: SoundboxEvent) => void, onStatus?: (live: boolean) => void): () => void {
  const es = new EventSource(`/api/events?key=${encodeURIComponent(KEY)}`);
  es.onmessage = m => fn(JSON.parse(m.data));
  es.onopen = () => onStatus?.(true);
  es.onerror = () => onStatus?.(false);
  return () => es.close();
}

export const rupees = (n: number) => `₹${n.toLocaleString('en-IN')}`;
