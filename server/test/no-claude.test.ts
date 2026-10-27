import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

test('nothing on the server calls an AI API', () => {
  const dir = new URL('../', import.meta.url);
  for (const f of readdirSync(dir).filter(f => f.endsWith('.ts'))) {
    const src = readFileSync(new URL(f, dir), 'utf8');
    assert.ok(!/@anthropic-ai\/sdk|ANTHROPIC_|messages\.create/.test(src), f);
  }
  const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'));
  assert.equal(pkg.dependencies?.['@anthropic-ai/sdk'], undefined);
});
