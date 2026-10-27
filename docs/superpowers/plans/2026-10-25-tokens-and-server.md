# Design Tokens and Server Additions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the server everything the Android app and the restyled web need — one design-tokens source, structured `display` and Devanagari `spoken` fields on what Munshi says, a `/api/brief` list, richer `paid` events, and a pairing QR on `/control` — without breaking the current web app.

**Architecture:** Two new pure modules (`server/display.ts` for the three-part on-screen line and brief titles, `server/spoken.ts` for Devanagari speech) are called from the existing routes in `server/app.ts`; nothing in detectors, executors or the agent changes behaviour. A small generator (`scripts/tokens.ts`) turns `design/tokens.json` into `web/tokens.css` and the Android `Tokens.kt`, and a test fails if the generated files drift from the JSON.

**Tech Stack:** Node ≥ 22.17 with `--experimental-strip-types`, TypeScript, `node:test`, React 18 + Vite (web), `qrcode` (already a dependency).

**Spec:** `docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md` (§3 tokens, §7 server changes)

## Global Constraints

- Every change is additive: existing response fields, event shapes and the 59 existing tests stay as they are.
- All money is whole rupees written `₹450`, Indian grouping via `toLocaleString('en-IN')`.
- Merchant-facing output never contains `payer_hash` or a customer's identity from payments.
- Spoken Devanagari keeps digits and `₹` exactly as in the Roman line.
- Kotlin package for generated code: `com.opuscube.munshi.ui.theme`.
- Work on branch `feat/app-redesign`; never commit to `master` directly.
- Run everything from the repo root `C:\Project\munshi\app`.

---

### Task 1: Branch and bring the design docs into the repo

**Files:**
- Create: `docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md` (copied)
- Create: `docs/design/soundbox-app-mockup.html` (copied)

- [ ] **Step 1: Create the branch**

```bash
git checkout -b feat/app-redesign
```

- [ ] **Step 2: Copy the spec and mockup from the parent folder**

```bash
cp ../docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md docs/superpowers/specs/
mkdir -p docs/design && cp ../docs/design/soundbox-app-mockup.html docs/design/
```

- [ ] **Step 3: Verify the baseline is green**

Run: `npm test && npm run typecheck`
Expected: `pass 59`, `fail 0`; tsc prints nothing.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md docs/design/soundbox-app-mockup.html docs/superpowers/plans/2026-10-25-tokens-and-server.md
git commit -m "docs: Android app and UI redesign spec, mockup and plan"
```

---

### Task 2: Design tokens and generator

**Files:**
- Create: `design/tokens.json`
- Create: `scripts/tokens.ts`
- Create (generated): `web/tokens.css`, `android/app/src/main/java/com/opuscube/munshi/ui/theme/Tokens.kt`
- Modify: `package.json` (add `"tokens"` script)
- Test: `server/test/tokens.test.ts`

**Interfaces:**
- Produces: `toCss(t: Tokens): string`, `toKotlin(t: Tokens): string`, `OUT_CSS`, `OUT_KT` exported from `scripts/tokens.ts`; CSS custom properties named `--<group>-<name>` in kebab case (e.g. `--color-paper`, `--radius-dock`, `--type-rupee-size`); Kotlin `object Tokens` with PascalCase members (`Tokens.Paper`, `Tokens.RadiusDock`, `Tokens.TypeRupeeSize`).

- [ ] **Step 1: Write the tokens file**

`design/tokens.json`:

```json
{
  "color": {
    "paper": "#F4EEE2",
    "rule": "#E7E0D2",
    "marginLine": "#B3261E73",
    "ink": "#1A1815",
    "ink2": "#5E5A52",
    "ink3": "#948E83",
    "red": "#B3261E",
    "redTop": "#C42F25",
    "redBottom": "#A7231B",
    "redOnInk": "#D2452F",
    "ivory": "#F4EEE2",
    "dockLine": "#4A453E"
  },
  "radius": { "dock": 26, "button": 18, "card": 14, "tag": 6, "pill": 999 },
  "space": { "ruleStep": 32, "marginLineX": 20, "dockInset": 10, "pageX": 20, "pageXWithMargin": 32 },
  "type": {
    "rupeeSize": 76, "rupeeTracking": -0.035,
    "whoSize": 19, "lineSize": 18, "lineHeight": 1.38,
    "kickerSize": 9, "kickerTracking": 0.16,
    "buttonSize": 17, "chipSize": 10
  },
  "motion": { "stampMs": 600, "bannerMs": 6500, "listenWindowMs": 6000 }
}
```

- [ ] **Step 2: Write the failing test**

`server/test/tokens.test.ts`:

```ts
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
```

- [ ] **Step 3: Run it to see it fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/tokens.test.ts`
Expected: FAIL — `Cannot find module '../../scripts/tokens.ts'`.

- [ ] **Step 4: Write the generator**

`scripts/tokens.ts`:

```ts
// design/tokens.json is the one source for colours, radii, spacing, type and motion.
// Run `npm run tokens` after editing it; a test fails if the outputs drift.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

type Group = Record<string, string | number>;
export type Tokens = Record<string, Group>;

const root = (p: string) => fileURLToPath(new URL(`../${p}`, import.meta.url));
export const SRC = root('design/tokens.json');
export const OUT_CSS = root('web/tokens.css');
export const OUT_KT = root('android/app/src/main/java/com/opuscube/munshi/ui/theme/Tokens.kt');

export const loadTokens = (): Tokens => JSON.parse(readFileSync(SRC, 'utf8'));

const kebab = (s: string) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
const pascal = (s: string) => s[0].toUpperCase() + s.slice(1);

const HEADER = 'Generated from design/tokens.json by scripts/tokens.ts. Do not edit.';

function cssValue(group: string, v: string | number): string {
  if (typeof v === 'string') return v;
  if (group === 'radius' || group === 'space') return `${v}px`;
  if (group === 'motion') return `${v}ms`;
  return String(v);
}

export function toCss(t: Tokens): string {
  const lines = Object.entries(t).flatMap(([g, vals]) =>
    Object.entries(vals).map(([k, v]) => `  --${g}-${kebab(k)}: ${cssValue(g, v)};`));
  return `/* ${HEADER} */\n:root {\n${lines.join('\n')}\n}\n`;
}

// "#RRGGBB" -> 0xFFRRGGBB, "#RRGGBBAA" -> 0xAARRGGBB (Compose wants alpha first).
function argb(hex: string): string {
  const h = hex.replace('#', '').toUpperCase();
  return h.length === 8 ? `0x${h.slice(6)}${h.slice(0, 6)}` : `0xFF${h}`;
}

function ktMember(group: string, k: string, v: string | number): string {
  if (group === 'color') return `  val ${pascal(k)} = Color(${argb(String(v))})`;
  const name = `${pascal(group)}${pascal(k)}`;
  if (group === 'motion') return `  const val ${name} = ${v}L`;
  return `  const val ${name} = ${v}f`;
}

export function toKotlin(t: Tokens): string {
  const body = Object.entries(t).flatMap(([g, vals]) => Object.entries(vals).map(([k, v]) => ktMember(g, k, v)));
  return `// ${HEADER}\npackage com.opuscube.munshi.ui.theme\n\nimport androidx.compose.ui.graphics.Color\n\nobject Tokens {\n${body.join('\n')}\n}\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const t = loadTokens();
  for (const [file, text] of [[OUT_CSS, toCss(t)], [OUT_KT, toKotlin(t)]] as const) {
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, text);
    console.log('wrote', file);
  }
}
```

Note: color members are `val` (not `const`), so `Tokens.Paper` is a `Color`; numbers are `Float` except motion, which is `Long`. The first test's `Paper` regex matches `val Paper = Color(0xFFF4EEE2)`.

- [ ] **Step 5: Add the npm script and generate**

In `package.json` `scripts`, add after `"test"`:

```json
    "tokens": "node --experimental-strip-types --no-warnings scripts/tokens.ts"
```

Run: `npm run tokens`
Expected: two `wrote …` lines; `web/tokens.css` and `android/app/src/main/java/com/opuscube/munshi/ui/theme/Tokens.kt` exist.

- [ ] **Step 6: Run the tests**

Run: `npm test`
Expected: `pass 61`, `fail 0`.

- [ ] **Step 7: Make sure typecheck covers the script**

Run: `npm run typecheck`
Expected: no output. If tsc reports `scripts/tokens.ts` is outside `include`, add `"scripts"` to the `include` array in `tsconfig.json` and rerun.

- [ ] **Step 8: Commit**

```bash
git add design/tokens.json scripts/tokens.ts web/tokens.css android/app/src/main/java/com/opuscube/munshi/ui/theme/Tokens.kt server/test/tokens.test.ts package.json tsconfig.json
git commit -m "feat: design tokens with CSS and Kotlin generator"
```

---

### Task 3: Three-part display lines and brief titles

**Files:**
- Create: `server/display.ts`
- Test: `server/test/display.test.ts`

**Interfaces:**
- Consumes: `OppRow` from `server/opportunities.ts` (`{ id, type, payload, estValue, … }`).
- Produces: `type Display = { who: string; amount: number; rest: string }`, `display(opp: OppRow): Display`, `briefTitle(opp: OppRow): string`.

- [ ] **Step 1: Write the failing test**

`server/test/display.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { refreshOpportunities } from '../detectors.ts';
import { getOpportunity, type OppRow } from '../opportunities.ts';
import { display, briefTitle } from '../display.ts';
import { AT, fresh } from './helpers.ts';

function all(): OppRow[] {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  return (db.prepare('SELECT id FROM opportunities ORDER BY id').all() as any[]).map(r => getOpportunity(db, r.id)!);
}
const byType = (type: string, pick: (p: any) => boolean = () => true) => all().find(o => o.type === type && pick(o.payload))!;

test('udhaar display splits who, amount and the question', () => {
  const d = display(byType('udhaar_overdue', p => p.customerName === 'Sharma ji'));
  assert.deepEqual(d, { who: 'Sharma ji ka', amount: 450, rest: 'udhaar 12 din se baaki hai. Reminder bhej doon?' });
});

test('every opportunity type has a display whose amount is a whole rupee figure from its payload', () => {
  const amountField: Record<string, string> = {
    udhaar_overdue: 'amount', stock_low: 'total', group_buy_open: 'saving',
    local_demand_spike: 'total', lapsed_regulars: 'offer', cash_gap: 'principal',
  };
  for (const o of all()) {
    const d = display(o);
    assert.equal(d.amount, o.payload[amountField[o.type]], o.type);
    assert.ok(Number.isInteger(d.amount), o.type);
    assert.ok(d.who.length > 0 && d.rest.length > 0, o.type);
    assert.ok(!/payer|hash/i.test(JSON.stringify(d)), o.type);
  }
});

test('cash gap display keeps the true cost and the daily deduction', () => {
  assert.deepEqual(display(byType('cash_gap')), {
    who: 'Diwali bill ke liye loan', amount: 50000, rest: 'Asli kharcha ₹1,240, roz ₹570 settlement se katega.',
  });
});

test('brief titles are short and name the task', () => {
  assert.equal(briefTitle(byType('udhaar_overdue', p => p.customerName === 'Sharma ji')), 'Sharma ji ka udhaar');
  assert.equal(briefTitle(byType('local_demand_spike', p => p.label === 'mithai')), 'Mithai ki maang upar');
  assert.equal(briefTitle(byType('group_buy_open')), 'Surf Excel 1kg group order');
  assert.equal(briefTitle(byType('stock_low')), 'Parle-G khatam ho raha');
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/display.test.ts`
Expected: FAIL — `Cannot find module '../display.ts'`.

- [ ] **Step 3: Write the module**

`server/display.ts`:

```ts
import type { OppRow } from './opportunities.ts';

// What the Soundbox screen sets in three parts: a small "who", a large ₹ figure, and the rest
// of the question in italic. The spoken and Roman lines stay whole; this is layout only.
export type Display = { who: string; amount: number; rest: string };

const rs = (n: number) => `₹${n.toLocaleString('en-IN')}`;
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

const displays: Record<string, (p: any) => Display> = {
  udhaar_overdue: p => ({ who: `${p.customerName} ka`, amount: p.amount, rest: `udhaar ${p.days} din se baaki hai. Reminder bhej doon?` }),
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
```

- [ ] **Step 4: Run the tests**

Run: `npm test`
Expected: `pass 65`, `fail 0`.

- [ ] **Step 5: Commit**

```bash
git add server/display.ts server/test/display.test.ts
git commit -m "feat: three-part display lines and brief titles for the app"
```

---

### Task 4: Devanagari speech for proposals and known lines

**Files:**
- Create: `server/lines.ts` (the two fixed agent lines, moved out of `agent.ts`)
- Modify: `server/agent.ts:18-19`
- Create: `server/spoken.ts`
- Test: `server/test/spoken.test.ts`

**Interfaces:**
- Consumes: `OppRow`; `DECLINED`, `NOT_UNDERSTOOD` from `server/lines.ts` (still re-exported by `server/agent.ts`, so existing imports keep working).
- Produces: `spokenFor(opp: OppRow): string` (Devanagari version of `template(opp)`), `toSpoken(text: string): string | undefined` (Devanagari for a known Roman line, else `undefined`).

- [ ] **Step 1: Write the failing test**

`server/test/spoken.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { refreshOpportunities } from '../detectors.ts';
import { getOpportunity } from '../opportunities.ts';
import { template } from '../phrase.ts';
import { DECLINED, NOT_UNDERSTOOD } from '../lines.ts';
import { spokenFor, toSpoken } from '../spoken.ts';
import { AT, fresh } from './helpers.ts';

const DEVANAGARI = /[\u0900-\u097F]/;
const nums = (s: string) => (s.match(/\d[\d,]*/g) ?? []).map(n => n.replace(/,/g, '')).sort();

test('every proposal has Devanagari speech with exactly the template\'s numbers', () => {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  for (const { id } of db.prepare('SELECT id FROM opportunities').all() as any[]) {
    const opp = getOpportunity(db, id)!;
    const s = spokenFor(opp);
    assert.match(s, DEVANAGARI, opp.type);
    assert.deepEqual(nums(s), nums(template(opp)), opp.type);
  }
});

test('udhaar speech reads naturally', () => {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  const id = (db.prepare("SELECT id FROM opportunities WHERE type='udhaar_overdue' AND json_extract(payload,'$.amount')=450").get() as any).id;
  assert.equal(spokenFor(getOpportunity(db, id)!), 'Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?');
});

test('toSpoken knows the lines Munshi says after an action or a payment', () => {
  const cases: Array<[string, string]> = [
    ['Sharma ji ne ₹450 chuka diye', 'Sharma ji ने ₹450 चुका दिए'],
    ['₹120 prapt hue', '₹120 प्राप्त हुए'],
    ['Sharma ji ko reminder bhej diya.', 'Sharma ji को रिमाइंडर भेज दिया।'],
    ['Jud gaye! 4 dukaanein, 8% bachat, ₹143 bache.', 'जुड़ गए! 4 दुकानें, 8% बचत, ₹143 बचे।'],
    ['₹50,000 aapke khaate mein aa gaye. Roz ₹570 settlement se katega.', '₹50,000 आपके खाते में आ गए। रोज़ ₹570 सेटलमेंट से कटेगा।'],
    ['Dhanyavaad! Yeh grahak ki 10vi khareed hai, agli baar ₹20 off.', 'धन्यवाद! यह ग्राहक की 10वीं खरीद है, अगली बार ₹20 की छूट।'],
    ['Ek purana grahak ₹15 offer se lauta! ₹340 ki khareed.', 'एक पुराना ग्राहक ₹15 ऑफ़र से लौटा! ₹340 की खरीद।'],
    ['Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.', 'शेल्फ़ देख ली: Surf Excel 1kg और Parle-G कम हैं।'],
    [DECLINED, 'ठीक है, रहने देते हैं।'],
    [NOT_UNDERSTOOD, 'माफ़ कीजिए, समझ नहीं आया। हाँ या नहीं दबाइए।'],
  ];
  for (const [roman, dev] of cases) assert.equal(toSpoken(roman), dev, roman);
});

test('toSpoken returns undefined for free text it does not know', () => {
  assert.equal(toSpoken('Aaj bikri achhi rahi, ₹3,200 aaye.'), undefined);
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/spoken.test.ts`
Expected: FAIL — `Cannot find module '../spoken.ts'`.

- [ ] **Step 3: Move the fixed lines out of `agent.ts`**

`spoken.ts` reads these at module load, and `agent.ts → phrase.ts → spoken.ts` would otherwise form an import cycle. Create `server/lines.ts`:

```ts
// Fixed lines Munshi says; kept apart so speech and the agent can share them without an import cycle.
export const DECLINED = 'Theek hai, rehne dete hain.';
export const NOT_UNDERSTOOD = 'Maaf kijiye, samajh nahi aaya. Haan ya Nahi dabaiye.';
```

In `server/agent.ts`, replace lines 18–19 (the two `export const` lines) with:

```ts
import { DECLINED, NOT_UNDERSTOOD } from './lines.ts';
export { DECLINED, NOT_UNDERSTOOD };
```

and move that `import` up with the other imports at the top of the file. In the test, change `import { DECLINED, NOT_UNDERSTOOD } from '../agent.ts';` to `from '../lines.ts'`.

- [ ] **Step 4: Write the module**

`server/spoken.ts`:

```ts
import type { OppRow } from './opportunities.ts';
import { DECLINED, NOT_UNDERSTOOD } from './lines.ts';

// A Hindi voice reads Devanagari well and Roman Hinglish badly, so the app speaks these
// when present. Names and product names stay in Roman (the voice reads them as English),
// and every digit and ₹ figure is copied exactly from the Roman line.

const rs = (n: number) => `₹${n.toLocaleString('en-IN')}`;

const proposals: Record<string, (p: any) => string> = {
  udhaar_overdue: p => `${p.customerName} का ₹${p.amount} उधार ${p.days} दिन से बाकी है। रिमाइंडर भेज दूँ?`,
  stock_low: p => `${p.name} खत्म होने वाला है, सिर्फ़ ${p.estQty} बचे। ${p.distributor} से ${p.qty} मँगवा दूँ? ₹${p.total}।`,
  group_buy_open: p => `${p.name} का ग्रुप ऑर्डर चल रहा है: आप जुड़ें तो ${p.members} दुकानें, ${p.pct}% बचत, ₹${p.saving} बचेंगे। जुड़ जाऊँ?`,
  local_demand_spike: p => `आपके मोहल्ले की ${p.shops} दुकानों में ${p.label} की बिक्री ${p.uplift}% ऊपर है। ${p.name} का स्टॉक बढ़ाऊँ? ${p.qty} पैक, ₹${p.total}।`,
  lapsed_regulars: p => `${p.count} पक्के ग्राहक 3 हफ़्ते से नहीं आए। उन्हें ₹${p.offer} का ऑफ़र भेजूँ? Paytm ऐप से जाएगा, नाम आपको नहीं दिखेगा।`,
  cash_gap: p => `${p.dueDays} दिन में दिवाली स्टॉक का ${rs(p.due)} का बिल है, पैसे ${rs(p.gap)} कम पड़ेंगे। ${rs(p.principal)} का लोन ले लें? असली खर्च ${rs(p.cost)}, रोज़ ${rs(p.daily)} सेटलमेंट से कटेगा।`,
};

export const spokenFor = (opp: OppRow): string => proposals[opp.type](opp.payload);

const R = '(₹[\\d,]+)';
const lines: Array<[RegExp, string | ((...m: string[]) => string)]> = [
  [new RegExp(`^(.+) ne ${R} chuka diye$`), '$1 ने $2 चुका दिए'],
  [new RegExp(`^${R} prapt hue$`), '$1 प्राप्त हुए'],
  [/^(.+) ko reminder bhej diya\.$/, '$1 को रिमाइंडर भेज दिया।'],
  [/^WhatsApp nahi gaya\. Yeh QR (.+) ko dikha dijiye\.$/, 'व्हाट्सऐप नहीं गया। यह QR $1 को दिखा दीजिए।'],
  [new RegExp(`^(.+) ko (\\d+) (.+) ka order bhej diya, ${R}\\.$`), '$1 को $2 $3 का ऑर्डर भेज दिया, $4।'],
  [new RegExp(`^Jud gaye! (\\d+) dukaanein, (\\d+)% bachat, ${R} bache\\.$`), 'जुड़ गए! $1 दुकानें, $2% बचत, $3 बचे।'],
  [new RegExp(`^(\\d+) purane grahakon ko Paytm app par ${R} ka offer bhej diya\\. Unki pehchaan aapko nahi dikhayi jaati\\.$`), '$1 पुराने ग्राहकों को Paytm ऐप पर $2 का ऑफ़र भेज दिया। उनकी पहचान आपको नहीं दिखाई जाती।'],
  [new RegExp(`^${R} aapke khaate mein aa gaye\\. Roz ${R} settlement se katega\\.$`), '$1 आपके खाते में आ गए। रोज़ $2 सेटलमेंट से कटेगा।'],
  [new RegExp(`^Dhanyavaad! Yeh grahak ki (\\d+)vi khareed hai, agli baar ${R} off\\.$`), 'धन्यवाद! यह ग्राहक की $1वीं खरीद है, अगली बार $2 की छूट।'],
  [new RegExp(`^Ek purana grahak ${R} offer se lauta! ${R} ki khareed\\.$`), 'एक पुराना ग्राहक $1 ऑफ़र से लौटा! $2 की खरीद।'],
  [/^Shelf dekh li: (.+) kam hain\.$/, (_m, names) => `शेल्फ़ देख ली: ${names.replace(/ aur /g, ' और ')} कम हैं।`],
  [/^Shelf dekh li, sab theek hai\.$/, 'शेल्फ़ देख ली, सब ठीक है।'],
];

const exact: Record<string, string> = {
  [DECLINED]: 'ठीक है, रहने देते हैं।',
  [NOT_UNDERSTOOD]: 'माफ़ कीजिए, समझ नहीं आया। हाँ या नहीं दबाइए।',
};

export function toSpoken(text: string): string | undefined {
  if (exact[text]) return exact[text];
  for (const [re, out] of lines) {
    if (re.test(text)) return typeof out === 'string' ? text.replace(re, out) : text.replace(re, out as any);
  }
  return undefined;
}
```

- [ ] **Step 5: Run the tests**

Run: `npm test && npm run typecheck`
Expected: `pass 69`, `fail 0`; tsc silent. If the "numbers" test fails for `stock_low`, compare `nums()` of both strings: the Devanagari template must carry `estQty`, `qty` and `total` exactly like `stockTemplate` in `server/inventory.ts`.

- [ ] **Step 6: Commit**

```bash
git add server/lines.ts server/agent.ts server/spoken.ts server/test/spoken.test.ts
git commit -m "feat: Devanagari speech for proposals and known lines"
```

---

### Task 5: Claude returns the Roman line and its Devanagari together

**Files:**
- Modify: `server/phrase.ts:28-49` (prompt, cache and a new `phraseFull`)
- Test: `server/test/phrase.test.ts`

**Interfaces:**
- Consumes: `spokenFor(opp)` from Task 4.
- Produces: `type Phrased = { line: string; spoken: string }`, `phraseFull(opp: OppRow, anthropic?: AnthropicLike): Promise<Phrased>`. `phrase(opp, anthropic)` keeps its signature and returns `(await phraseFull(...)).line`.

- [ ] **Step 1: Write the failing test**

`server/test/phrase.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity } from '../opportunities.ts';
import { phraseFull, template } from '../phrase.ts';
import { spokenFor } from '../spoken.ts';
import { AT, fresh } from './helpers.ts';

const reply = (text: string) => ({ messages: { create: async () => ({ stop_reason: 'end_turn', content: [{ type: 'text', text }] }) } });

test('without Claude: template line and template Devanagari', async () => {
  const opp = nextOpportunity(fresh(), HERO_ID, AT)!;
  assert.deepEqual(await phraseFull(opp), { line: template(opp), spoken: spokenFor(opp) });
});

test('Claude gives both lines: Roman first, Devanagari second', async () => {
  const db = fresh();
  db.prepare("UPDATE khata SET amount=451 WHERE customer_name='Sharma ji'").run(); // fresh cache key
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await phraseFull(opp, reply('Ramesh ji, Sharma ji ka ₹451 udhaar 12 din se baaki hai. Reminder bhej doon?\nरमेश जी, Sharma ji का ₹451 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?'));
  assert.equal(r.line, 'Ramesh ji, Sharma ji ka ₹451 udhaar 12 din se baaki hai. Reminder bhej doon?');
  assert.equal(r.spoken, 'रमेश जी, Sharma ji का ₹451 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?');
});

test('Claude Devanagari that drops a number falls back to the template Devanagari', async () => {
  const db = fresh();
  db.prepare("UPDATE khata SET amount=452 WHERE customer_name='Sharma ji'").run();
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await phraseFull(opp, reply('Sharma ji ka ₹452 udhaar 12 din se baaki hai. Reminder bhej doon?\nशर्मा जी का उधार बाकी है।'));
  assert.equal(r.line, 'Sharma ji ka ₹452 udhaar 12 din se baaki hai. Reminder bhej doon?');
  assert.equal(r.spoken, spokenFor(opp));
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/phrase.test.ts`
Expected: FAIL — `phraseFull` is not exported.

- [ ] **Step 3: Replace the prompt, cache and `phrase` in `server/phrase.ts`**

Replace lines 28–49 (from `const REWRITE` to the end of the file) with:

```ts
const REWRITE = 'Aap ek kirana dukaan ke Soundbox ki awaaz hain. Diye gaye vaakya ko garamjoshi bhare 1-2 chhote Hinglish (Roman script) vaakyon mein dobara likhiye. Har naam, sankhya aur ₹ raashi bilkul wahi rakhiye, aur aakhri sawaal bhi. Pehli line mein Roman script waala vaakya likhiye. Doosri line mein wahi vaakya Devanagari lipi mein likhiye: sankhyayein aur ₹ ankon mein hi rahein, aur naam Roman mein. Sirf ye do lines, aur kuch nahi.';

export type Phrased = { line: string; spoken: string };
const cache = new Map<string, Phrased>();
const numbers = (s: string) => s.match(/\d+/g) ?? [];
const DEVANAGARI = /[\u0900-\u097F]/;

// Claude rewording with a hard 4s ceiling; the templates are always a safe answer.
export async function phraseFull(opp: OppRow, anthropic?: AnthropicLike): Promise<Phrased> {
  const base = template(opp);
  const fallback = { line: base, spoken: spokenFor(opp) };
  if (cache.has(base)) return cache.get(base)!;
  if (!anthropic) return fallback;
  try {
    const res = await withTimeout(anthropic.messages.create({
      model: MODEL, max_tokens: 1000, output_config: { effort: 'low' },
      system: REWRITE, messages: [{ role: 'user', content: base }],
    }), 4000);
    const text = (res.content ?? []).filter((b: any) => b.type === 'text').map((b: any) => b.text).join('\n');
    const [line = '', dev = ''] = text.split('\n').map((s: string) => s.trim()).filter(Boolean);
    const keeps = (s: string) => numbers(base).every(n => s.includes(n));
    if (res.stop_reason === 'refusal' || !line || DEVANAGARI.test(line) || !keeps(line)) return fallback;
    const out = { line, spoken: DEVANAGARI.test(dev) && keeps(dev) ? dev : fallback.spoken };
    cache.set(base, out);
    return out;
  } catch {
    return fallback;
  }
}

export async function phrase(opp: OppRow, anthropic?: AnthropicLike): Promise<string> {
  return (await phraseFull(opp, anthropic)).line;
}
```

And add to the imports at the top of `server/phrase.ts`:

```ts
import { spokenFor } from './spoken.ts';
```

- [ ] **Step 4: Run the tests**

Run: `npm test && npm run typecheck`
Expected: `pass 72`, `fail 0`; the two existing `phrase falls back …` tests in `agent.test.ts` still pass; tsc prints nothing.

- [ ] **Step 5: Commit**

```bash
git add server/phrase.ts server/test/phrase.test.ts
git commit -m "feat: Claude returns the Hinglish line with its Devanagari"
```

---

### Task 6: Wire the API — brief, display, spoken, richer events

**Files:**
- Modify: `server/events.ts:3-10` (event types)
- Modify: `server/opportunities.ts` (add `briefList`)
- Modify: `server/payments.ts:14-34` (`payLink` also returns `name`, `amount`)
- Modify: `server/shelf.ts:38-40` (`next` gains `display`, `spoken`)
- Modify: `server/app.ts` (routes `/api/next`, confirm, decline, converse, pay, control routes; new `/api/brief`)
- Test: `server/test/app-additions.test.ts`

**Interfaces:**
- Consumes: `display`, `briefTitle` (Task 3); `spokenFor`, `toSpoken` (Task 4); `phraseFull` (Task 5).
- Produces (HTTP contract the Android app relies on):
  - `POST /api/next` → `{ id: number | null, type?, line?, spoken?, display?: { who, amount, rest } }`
  - `GET /api/brief` → `{ items: Array<{ id, type, title, amount }>, total }` (top 3 brief-scope tasks by value; does not change any status)
  - confirm / decline / converse responses gain `spoken?: string`
  - shelf result `next` → `{ id, line, spoken, display } | null`
  - events: `{ type: 'announce', text, spoken? }`, `{ type: 'paid', ref, name, amount }`, `{ type: 'shelf', line, spoken?, next }`

- [ ] **Step 1: Write the failing test**

`server/test/app-additions.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { openDb } from '../db.ts';
import { seed } from '../seed.ts';
import { now } from '../clock.ts';
import { createApp } from '../app.ts';
import { onEvent } from '../events.ts';
import { deps } from './helpers.ts';

async function boot() {
  const db = openDb(':memory:');
  seed(db, now());
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body = {}) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json());
  const get = (p: string) => fetch(base + p).then(r => r.json());
  return { db, server, post, get };
}

// The HTTP tests seed from the real clock, and the demand-spike value moves with the time of
// day, so check order and sums rather than exact spike rupees.
test('brief lists the top three tasks with values and leaves them unproposed', async () => {
  const { db, server, get } = await boot();
  try {
    const b = await get('/api/brief');
    const amounts = b.items.map((i: any) => i.amount);
    assert.equal(b.items.length, 3);
    assert.deepEqual(b.items[0], { id: b.items[0].id, type: 'udhaar_overdue', title: 'Sharma ji ka udhaar', amount: 450 });
    assert.deepEqual(amounts, [...amounts].sort((x: number, y: number) => y - x));
    assert.equal(b.total, amounts.reduce((s: number, n: number) => s + n, 0));
    assert.ok(b.items.every((i: any) => ['udhaar_overdue', 'stock_low', 'group_buy_open', 'local_demand_spike'].includes(i.type)));
    assert.equal((db.prepare("SELECT COUNT(*) n FROM opportunities WHERE status='proposed'").get() as any).n, 0);
  } finally { server.close(); }
});

test('next carries display and Devanagari; confirm and decline carry spoken', async () => {
  const { server, post } = await boot();
  try {
    const next = await post('/api/next');
    assert.deepEqual(next.display, { who: 'Sharma ji ka', amount: 450, rest: 'udhaar 12 din se baaki hai. Reminder bhej doon?' });
    assert.equal(next.spoken, 'Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?');
    const r = await post(`/api/opportunities/${next.id}/confirm`);
    assert.equal(r.spoken, 'Sharma ji को रिमाइंडर भेज दिया।');
    const n2 = await post('/api/next');
    assert.equal((await post(`/api/opportunities/${n2.id}/decline`)).spoken, 'ठीक है, रहने देते हैं।');
  } finally { server.close(); }
});

test('paying a link publishes paid with name and amount, and a spoken announcement', async () => {
  const { server, post } = await boot();
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const next = await post('/api/next');
    const ref = (await post(`/api/opportunities/${next.id}/confirm`)).payUrl.split('/').pop();
    await post(`/api/pay/${ref}`);
    assert.deepEqual(events.find(e => e.type === 'paid'), { type: 'paid', ref, name: 'Sharma ji', amount: 450 });
    assert.deepEqual(events.find(e => e.type === 'announce'), { type: 'announce', text: 'Sharma ji ne ₹450 chuka diye', spoken: 'Sharma ji ने ₹450 चुका दिए' });
  } finally { off(); server.close(); }
});

test('shelf beat: next offer has display and spoken; walk-in announcement is spoken', async () => {
  const { server, post } = await boot();
  const events: any[] = [];
  const off = onEvent(e => events.push(e));
  try {
    const r = await post('/api/control/shelf');
    assert.equal(r.next.display.who, 'Surf Excel 1kg group order');
    assert.match(r.next.spoken, /ग्रुप ऑर्डर/);
    assert.equal(r.spoken, 'शेल्फ़ देख ली: Surf Excel 1kg और Parle-G कम हैं।');
    await post('/api/control/walkin', { amount: 120 });
    assert.deepEqual(events.at(-1), { type: 'announce', text: '₹120 prapt hue', spoken: '₹120 प्राप्त हुए' });
  } finally { off(); server.close(); }
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `node --experimental-strip-types --no-warnings --test server/test/app-additions.test.ts`
Expected: FAIL — `/api/brief` returns 404 and `next.display` is `undefined`.

- [ ] **Step 3: Widen the event types**

In `server/events.ts`, replace the `SoundboxEvent` type (lines 3–10) with:

```ts
export type Display = { who: string; amount: number; rest: string };
export type Offer = { id: number; line: string; spoken?: string; display?: Display };

export type SoundboxEvent =
  | { type: 'announce'; text: string; spoken?: string }
  | { type: 'brief' }
  | { type: 'nudge' }
  | { type: 'ledger' }
  | { type: 'paid'; ref: string; name?: string; amount?: number }
  | { type: 'reset' }
  | { type: 'shelf'; line: string; spoken?: string; next: Offer | null };
```

- [ ] **Step 4: Add `briefList` to `server/opportunities.ts`**

Append:

```ts
import { briefTitle } from './display.ts';

export type BriefItem = { id: number; type: string; title: string; amount: number };

// The morning list, read-only: top three brief-scope tasks by value. Nothing is proposed here;
// the session still asks them one at a time through nextOpportunity.
export function briefList(db: DB, merchantId: string, at: Date): { items: BriefItem[]; total: number } {
  refreshOpportunities(db, merchantId, at);
  const rows = db.prepare(
    `SELECT id FROM opportunities WHERE merchant_id=? AND status IN ('new','proposed')
     AND type IN (${BRIEF_TYPES.map(() => '?').join(',')}) ORDER BY est_value DESC, id LIMIT 3`,
  ).all(merchantId, ...BRIEF_TYPES) as any[];
  const items = rows.map(r => getOpportunity(db, r.id)!).map(o => ({ id: o.id, type: o.type, title: briefTitle(o), amount: o.estValue }));
  return { items, total: items.reduce((s, i) => s + i.amount, 0) };
}
```

Place the `import` line with the other imports at the top of the file, not mid-file.

- [ ] **Step 5: Return name and amount from `payLink`**

In `server/payments.ts`, change the `payLink` return type and both returns:

```ts
export function payLink(db: DB, ref: string, at: Date): { alreadyPaid: boolean; announcement: string; name: string; amount: number } | null {
```

```ts
    if (flipped.changes === 0) { db.exec('COMMIT'); return { alreadyPaid: true, announcement, name: r.customer_name, amount: r.amount }; }
```

```ts
  return { alreadyPaid: false, announcement, name: r.customer_name, amount: r.amount };
```

Then update the one assertion in `server/test/payments.test.ts:19` to:

```ts
  assert.deepEqual(payLink(db, ref, AT), { alreadyPaid: false, announcement: 'Sharma ji ne ₹450 chuka diye', name: 'Sharma ji', amount: 450 });
```

- [ ] **Step 6: Give the shelf offer its display and speech**

In `server/shelf.ts`, add imports:

```ts
import { display } from './display.ts';
import { spokenFor, toSpoken } from './spoken.ts';
```

and replace the last two lines of `shelfFlow` (lines 39–40) with:

```ts
  const opp = getOpportunity(db, proposeOpportunity(db, merchantId, best.id).id)!;
  return { line, spoken: toSpoken(line), next: { id: opp.id, line: template(opp), spoken: spokenFor(opp), display: display(opp) } };
```

and the early return on line 38 with:

```ts
  if (!best) return { line, spoken: toSpoken(line), next: null };
```

- [ ] **Step 7: Wire the routes in `server/app.ts`**

Add imports:

```ts
import { phraseFull } from './phrase.ts';
import { display } from './display.ts';
import { toSpoken } from './spoken.ts';
import { briefList } from './opportunities.ts';
```

(merge `briefList` into the existing `./opportunities.ts` import line, and drop `phrase` from the `./phrase.ts` import if it becomes unused.)

Add two helpers after `const UNREADABLE = …`:

```ts
const announce = (text: string) => publish({ type: 'announce', text, spoken: toSpoken(text) });
const withSpoken = <T extends { line: string }>(r: T) => ({ ...r, spoken: toSpoken(r.line) });
```

Then change these routes (each replacement shown in full):

```ts
  ['POST', /^\/api\/next$/, async ({ db, deps, body }) => {
    const at = now();
    if (body.interrupt && !shouldInterrupt(db, HERO_ID, at)) return { id: null };
    const opp = nextOpportunity(db, HERO_ID, at, body.scope === 'all' ? 'all' : 'brief');
    if (!opp) return { id: null };
    const { line, spoken } = await phraseFull(opp, deps.anthropic);
    return { id: opp.id, type: opp.type, line, spoken, display: display(opp) };
  }],
  ['GET', /^\/api\/brief$/, ({ db }) => briefList(db, HERO_ID, now())],
  ['POST', /^\/api\/opportunities\/(\d+)\/confirm$/, async ({ db, deps, params }) => {
    const r = await confirmOpportunity(db, Number(params[0]), deps, now());
    publish({ type: 'ledger' });
    return withSpoken(r);
  }],
  ['POST', /^\/api\/opportunities\/(\d+)\/decline$/, ({ db, params }) => { declineOpportunity(db, Number(params[0])); return withSpoken({ line: DECLINED }); }],
  ['POST', /^\/api\/converse$/, async ({ db, deps, body }) => {
    if (typeof body.text !== 'string' || !body.text.trim()) throw new HttpError(400, 'text required');
    return withSpoken(await converse(db, HERO_ID, body.text, now(), deps));
  }],
```

In `POST /api/pay/:ref`, replace the publish line with:

```ts
    if (!r.alreadyPaid) { announce(r.announcement); publish({ type: 'paid', ref: params[0], name: r.name, amount: r.amount }); publish({ type: 'ledger' }); }
```

In `handleWhatsApp` and the `/api/shelf` NoVision/Unreadable branches, replace `publish({ type: 'announce', text: … })` with `announce(…)`. In `/api/control/regular` and `/api/control/returning`, replace `publish({ type: 'announce', text })` with `announce(text)`. In `/api/control/walkin`, replace the publish with `announce(recordWalkin(db, HERO_ID, amount, now()));`.

`publishShelf` already spreads `shelfFlow`'s result into the event, so `spoken` and the richer `next` flow through unchanged.

- [ ] **Step 8: Run all tests and the type check**

Run: `npm test && npm run typecheck`
Expected: `pass 76`, `fail 0`; tsc prints nothing. The existing `finale core loop over HTTP` test still passes because it only reads `next.line`, `payUrl` and the announce text.

- [ ] **Step 9: Commit**

```bash
git add server/events.ts server/opportunities.ts server/payments.ts server/shelf.ts server/app.ts server/test/app-additions.test.ts server/test/payments.test.ts
git commit -m "feat: brief list, display and Devanagari on the API, richer paid events"
```

---

### Task 7: Pairing QR on /control

**Files:**
- Modify: `web/api.ts:31-32` (export the demo key)
- Modify: `web/Control.tsx` (pairing section)

**Interfaces:**
- Produces: QR payload, exactly `{"url":"<origin>","key":"<demo key or empty>"}` — the Android `pairing` package parses this.

- [ ] **Step 1: Export the key**

In `web/api.ts`, right after the `const KEY = …` line, add:

```ts
export const demoKey = KEY;
```

- [ ] **Step 2: Render the pairing QR on /control**

In `web/Control.tsx`, change the imports to:

```tsx
import { useEffect, useState, type ReactNode } from 'react';
import QRCode from 'qrcode';
import { api, demoKey } from './api.ts';
import { Nav } from './ui.tsx';
```

Inside `Control()`, after the existing `useState` lines, add:

```tsx
  const [pairQr, setPairQr] = useState<string | null>(null);
  useEffect(() => {
    // The app reads this to find the server and the demo key; /control is opened on the public URL.
    const payload = JSON.stringify({ url: location.origin, key: demoKey });
    QRCode.toDataURL(payload, { margin: 1, width: 280 }).then(setPairQr).catch(() => setPairQr(null));
  }, []);
```

And just before `<h2 className="section-title">Log</h2>`, add:

```tsx
        <h2 className="section-title">Soundbox app jodiye</h2>
        <div className="control">
          <div><h2>Pairing QR</h2><p>Munshi app kholiye, “Jodiye” par yeh QR scan kijiye. Server ka pata aur demo key isi mein hai.</p></div>
          {pairQr ? <img src={pairQr} alt="Munshi app pairing QR" width={140} height={140} /> : <span>QR nahi bana</span>}
        </div>
```

- [ ] **Step 3: Type-check and build**

Run: `npm run typecheck && npm run build`
Expected: tsc prints nothing; Vite prints `✓ built in …`.

- [ ] **Step 4: Check it in a browser**

Run: `npm run server` (separate terminal), open `http://localhost:3000/control`.
Expected: a "Soundbox app jodiye" section with a QR. Decoding it (any phone QR reader) gives `{"url":"http://localhost:3000","key":""}`.

- [ ] **Step 5: Commit**

```bash
git add web/api.ts web/Control.tsx
git commit -m "feat: pairing QR for the Android app on /control"
```

---

### Task 8: Document the API additions

**Files:**
- Modify: `README.md` (new "App API" section after the Routes table)

- [ ] **Step 1: Add the section**

Insert after the Routes table in `README.md`:

```markdown
### App API (used by the Android Soundbox app)

| Call | Returns |
|---|---|
| `GET /api/brief` | `{ items: [{ id, type, title, amount }], total }`: today's top three tasks, read-only |
| `POST /api/next` | `{ id, type, line, spoken, display: { who, amount, rest } }` |
| confirm / decline / converse | the result `line` plus `spoken` (Devanagari) when known |
| `GET /api/events` (SSE) | `announce` gains `spoken`; `paid` gains `name`, `amount`; `shelf.next` gains `spoken`, `display` |

`spoken` is the same line in Devanagari for a Hindi voice; when it is missing, speak `line` with an Indian English voice. Design tokens live in `design/tokens.json`; run `npm run tokens` after editing them.
```

- [ ] **Step 2: Final full check**

Run: `npm test && npm run typecheck && npm run build`
Expected: `pass 76`, `fail 0`; tsc silent; Vite build succeeds.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: app API and design tokens in the README"
```
