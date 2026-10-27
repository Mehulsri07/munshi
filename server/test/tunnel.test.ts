import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tunnelUrl } from '../tunnel.ts';

test('finds the Quick Tunnel address in cloudflared output', () => {
  const out = `2026-10-25T10:00:00Z INF Requesting new quick Tunnel on trycloudflare.com...
2026-10-25T10:00:02Z INF +--------------------------------------------------------------------------------------------+
2026-10-25T10:00:02Z INF |  https://quiet-river-1234-abc.trycloudflare.com                                             |`;
  assert.equal(tunnelUrl(out), 'https://quiet-river-1234-abc.trycloudflare.com');
  assert.equal(tunnelUrl('INF Starting metrics server on 127.0.0.1:20241/metrics'), null);
  assert.equal(tunnelUrl('see https://www.cloudflare.com/website-terms/'), null);
  assert.equal(tunnelUrl('ERR failed to request quick Tunnel: Post "https://api.trycloudflare.com/tunnel"'), null);
});
