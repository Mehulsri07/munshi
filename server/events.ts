import type { ServerResponse } from 'node:http';

export type Display = { who: string; amount: number; rest: string };
export type Offer = { id: number; line: string; spoken?: string; display?: Display };

export type SoundboxEvent =
  | { type: 'announce'; text: string; spoken?: string }
  | { type: 'brief' }
  | { type: 'nudge' }
  | { type: 'ledger' }
  | { type: 'paid'; ref: string; name?: string; amount?: number }
  | { type: 'reset' }
  | { type: 'shelf'; line: string; spoken?: string; next: Offer | null }
  | { type: 'expired'; ref: string; name: string; amount: number };

const listeners = new Set<(e: SoundboxEvent) => void>();

export function onEvent(fn: (e: SoundboxEvent) => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

// Quick Tunnels drop SSE, so the phone long-polls. Recent events wait here, numbered, for it to collect.
export const EVENT_BUFFER = 200;
let seq = 0;
let recent: Array<{ seq: number; e: SoundboxEvent }> = [];
const waiting = new Set<() => void>();

export function publish(e: SoundboxEvent): void {
  seq++;
  if (e.type === 'reset') recent = [];   // nothing before a reset is worth replaying
  recent.push({ seq, e });
  if (recent.length > EVENT_BUFFER) recent.shift();
  for (const fn of listeners) fn(e);
  for (const wake of [...waiting]) wake();
}

export type Polled = { events: Array<{ seq: number } & SoundboxEvent>; seq: number } | { resync: true; seq: number };

// What a client missed since `after`. No cursor = start from now. A gap the buffer cannot fill
// (overrun, reset, server restart) = resync: the client re-fetches its state instead.
export function since(after?: number): Polled {
  if (after === undefined) return { events: [], seq };
  const oldest = recent[0]?.seq ?? seq + 1;
  if (after > seq || after < oldest - 1) return { resync: true, seq };
  return { events: recent.filter(r => r.seq > after).map(r => ({ seq: r.seq, ...r.e })), seq };
}

export function poll(after: number | undefined, waitMs: number): Promise<Polled> {
  const first = since(after);
  if (after === undefined || !('events' in first) || first.events.length) return Promise.resolve(first);
  return new Promise(resolve => {
    const wake = () => { clearTimeout(timer); waiting.delete(wake); resolve(since(after)); };
    const timer = setTimeout(wake, waitMs);
    waiting.add(wake);
  });
}

export function subscribe(res: ServerResponse): void {
  res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
  res.write(': connected\n\n');
  const off = onEvent(e => res.write(`data: ${JSON.stringify(e)}\n\n`));
  const ping = setInterval(() => res.write(': ping\n\n'), 25_000); // keeps tunnels from closing idle streams
  res.on('close', () => { off(); clearInterval(ping); });
}
