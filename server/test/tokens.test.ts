import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { toCss, toKotlin, loadTokens, OUT_CSS, OUT_KT } from '../../scripts/tokens.ts';

test('tokens render to CSS custom properties and a Kotlin object', () => {
  const t = loadTokens();
  const css = toCss(t);
  assert.match(css, /--color-paper: #F4EEE2;/);
  assert.match(css, /--color-margin-line: #B3261E73;/);
  assert.match(css, /--radius-dock: 26px;/);
  assert.match(css, /--space-page-x-with-margin: 32px;/);
  assert.match(css, /--type-line-height: 1.38;/);
  assert.match(css, /--motion-banner-ms: 6500ms;/);
  const kt = toKotlin(t);
  assert.match(kt, /^package com\.opuscube\.munshi\.ui\.theme/m);
  assert.match(kt, /val Paper = Color\(0xFFF4EEE2\)/);
  assert.match(kt, /val MarginLine = Color\(0x73B3261E\)/);
  assert.match(kt, /const val RadiusDock = 26f/);
  assert.match(kt, /const val TypeRupeeTracking = -0.035f/);
  assert.match(kt, /const val MotionBannerMs = 6500L/);
});

test('generated files are up to date with design/tokens.json (run npm run tokens)', () => {
  const t = loadTokens();
  assert.equal(readFileSync(OUT_CSS, 'utf8'), toCss(t));
  assert.equal(readFileSync(OUT_KT, 'utf8'), toKotlin(t));
});
