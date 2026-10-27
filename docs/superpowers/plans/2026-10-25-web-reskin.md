# Web Re-skin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every web surface (`/pay`, `/ledger`, `/paytm`, `/control`, and `/soundbox` as the stage fallback) the same "ledger page + ink dock" look as the Android app and the Round 1 deck.

**Architecture:** Keep every React component and class name; replace the indigo/saffron stylesheet with one built on the generated `web/tokens.css` (from plan a) and self-hosted Fraunces + Inter. Markup changes are limited to the wordmark, the raised ₹ in captions, a dock wrapper on the Soundbox, a "Chuka diya" stamp on the pay page, and the QR ink colour.

**Tech Stack:** React 18 + Vite, plain CSS custom properties, `@fontsource-variable/fraunces`, `@fontsource-variable/inter`.

**Spec:** `docs/superpowers/specs/2026-10-25-android-app-and-ui-design.md` (§3 visual language, §8 web re-skin). Depends on plan `2026-10-25-tokens-and-server.md` (provides `web/tokens.css`).

## Global Constraints

- Colours, radii, spacing and type sizes come from `web/tokens.css` variables (`--color-*`, `--radius-*`, `--space-*`, `--type-*`); no new hex values except `#fff` for QR/card backgrounds.
- `--type-*` tokens are unitless numbers: use them as `calc(var(--type-x) * 1px)` for sizes and `calc(var(--type-x) * 1em)` for tracking; `--type-line-height` is used bare. `--radius-*`, `--space-*` already carry `px`.
- Red is the single accent: kickers, the Haan button, the stamp, live dots. Money figures are ink, not green.
- The pay page keeps saying the payment is simulated, and must stay fast on mobile data (no new runtime JS dependencies).
- `prefers-reduced-motion` turns animation off, as today.
- All existing tests, `npm run typecheck`, `npm run build` and the CI Docker smoke test keep passing.
- Work on branch `feat/app-redesign`.

---

### Task 1: Fonts and tokens wired in

**Files:**
- Modify: `package.json` (font packages)
- Modify: `web/main.tsx:2-7`

- [ ] **Step 1: Swap the font packages**

```bash
npm uninstall @fontsource-variable/anek-latin @fontsource/mukta
npm install -D @fontsource-variable/fraunces @fontsource-variable/inter
ls node_modules/@fontsource-variable/fraunces/*.css node_modules/@fontsource-variable/inter/*.css
```

Expected: the listing includes `fraunces/full.css` and `fraunces/full-italic.css` (all axes including optical size) and `inter/index.css`. If the file names differ, use the ones listed that contain the `opsz` axis.

- [ ] **Step 2: Import them with the tokens**

In `web/main.tsx`, replace the four font/style imports (lines 3–7) with:

```tsx
import '@fontsource-variable/fraunces/full.css';
import '@fontsource-variable/fraunces/full-italic.css';
import '@fontsource-variable/inter';
import './tokens.css';
import './styles.css';
```

- [ ] **Step 3: Build**

Run: `npm run build`
Expected: `✓ built`; `web/dist/assets` contains `fraunces-*.woff2` and `inter-*.woff2`.

- [ ] **Step 4: Commit**

```bash
git add package.json package-lock.json web/main.tsx
git commit -m "feat(web): self-hosted Fraunces and Inter, design tokens loaded"
```

---

### Task 2: The ledger stylesheet

**Files:**
- Modify: `web/styles.css` (full replacement)

- [ ] **Step 1: Replace `web/styles.css` with**

```css
/* Ledger page + ink dock. Tokens come from tokens.css (generated from design/tokens.json). */
:root {
  --font-display: 'Fraunces Variable', Georgia, serif;
  --font-body: 'Inter Variable', system-ui, sans-serif;
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --gutter: 20px;
  --stage-max: 560px;
  --monument: clamp(64px, 19vw, 112px);
  --caption-lg: clamp(22px, 6vw, 30px);
  --caption-sm: 20px;
  --ink-line: color-mix(in srgb, var(--color-ink) 14%, transparent);
}

*, *::before, *::after { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body {
  margin: 0;
  font-family: var(--font-body); font-size: 16px; line-height: 1.5;
  color: var(--color-ink);
  background-color: var(--color-paper);
  background-image: repeating-linear-gradient(to bottom, transparent 0 calc(var(--space-rule-step) - 1px), var(--color-rule) calc(var(--space-rule-step) - 1px) var(--space-rule-step));
  -webkit-font-smoothing: antialiased;
}
/* The red ledger margin line, aligned just left of the content column. */
body::before {
  content: ''; position: fixed; top: 0; bottom: 0; z-index: 0; width: 1px; pointer-events: none;
  left: max(var(--space-margin-line-x), calc((100vw - var(--col, 720px)) / 2 - 12px));
  background: var(--color-margin-line);
}
#root { position: relative; z-index: 1; }
button, input, select { font: inherit; color: inherit; }
::selection { background: var(--color-red); color: var(--color-ivory); }
:focus-visible { outline: 2px solid var(--color-red); outline-offset: 3px; }
.num { font-variant-numeric: lining-nums tabular-nums; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.rupee { font-size: 0.48em; vertical-align: 0.62em; margin-right: 0.04em; font-weight: 400; }

/* ---------- Top bar ---------- */
.bar {
  display: flex; align-items: center; gap: 12px; min-height: 56px;
  padding: env(safe-area-inset-top) max(var(--gutter), calc((100vw - var(--col, 720px)) / 2)) 0;
  border-bottom: 1px solid var(--color-rule);
}
.wordmark { font-family: var(--font-display); font-weight: 600; font-size: 19px; letter-spacing: -0.01em; display: inline-flex; align-items: center; }
.wordmark i { width: 6px; height: 6px; border-radius: 50%; background: var(--color-red); margin-left: 4px; }
.bar .spacer { flex: 1; }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-ink2); }
.live i { width: 7px; height: 7px; border-radius: 50%; background: var(--color-ink3); }
.live[data-on='true'] i { background: var(--color-red); animation: pulse 2.4s var(--ease-out) infinite; }
.clock { font-size: 13px; color: var(--color-ink2); }
.week-total { font-size: calc(var(--type-chip-size) * 1px); color: var(--color-ink2); border: 1px solid var(--color-rule); border-radius: var(--radius-pill); padding: 5px 10px; background: rgb(255 255 255 / 0.45); }
.week-total b { color: var(--color-ink); font-weight: 600; }
.nav { display: flex; gap: 4px; overflow-x: auto; scrollbar-width: none; }
.nav a { color: var(--color-ink2); text-decoration: none; font-size: 14px; font-weight: 500; white-space: nowrap; padding: 6px 12px; border-radius: var(--radius-pill); }
.nav a:hover { color: var(--color-ink); }
.nav a[aria-current='page'] { background: var(--color-ink); color: var(--color-ivory); }

/* ---------- Kicker (section labels, "Munshi bol raha hai") ---------- */
.kicker, .section-title, .onair, .score-head, .impact-head {
  font-family: var(--font-body); font-weight: 700; font-size: 11px; letter-spacing: calc(var(--type-kicker-tracking) * 1em);
  text-transform: uppercase; color: var(--color-red);
}

/* ---------- Buttons ---------- */
.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 10px;
  min-height: 54px; padding: 0 22px; border: 0; border-radius: var(--radius-button);
  font-family: var(--font-body); font-weight: 700; font-size: calc(var(--type-button-size) * 1px);
  cursor: pointer; touch-action: manipulation;
  transition: transform 160ms var(--ease-out), filter 160ms var(--ease-out);
}
.btn svg { width: 22px; height: 22px; flex: none; }
.btn:active:not(:disabled) { transform: scale(0.97); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-ask {
  background: linear-gradient(180deg, var(--color-red-top), var(--color-red-bottom)); color: var(--color-ivory);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.18), 0 6px 16px rgb(179 38 30 / 0.3);
}
.btn-ask:hover:not(:disabled) { filter: brightness(1.06); }
.btn-ghost { background: transparent; color: var(--color-ink); box-shadow: inset 0 0 0 1px var(--color-ink); }
.btn-plain { background: var(--color-ink); color: var(--color-ivory); }
.btn-wide { width: 100%; }

/* ---------- The ink dock ---------- */
.dock {
  background: var(--color-ink); color: var(--color-ivory); border-radius: var(--radius-dock);
  padding: 14px; display: grid; gap: 12px;
  box-shadow: 0 12px 30px rgb(26 24 21 / 0.35), inset 0 1px 0 rgb(255 255 255 / 0.07);
}
.dock .btn-ghost { color: var(--color-ivory); box-shadow: inset 0 0 0 1px var(--color-dock-line); }
.dock .heard { color: var(--color-ink3); text-align: center; font-size: 12px; min-height: 18px; }
.dock .heard q { color: var(--color-ivory); }

/* ---------- Caption: what Munshi says ---------- */
.caption-strip {
  margin: 0; font-family: var(--font-display); font-style: italic; font-weight: 400;
  font-size: var(--caption-lg); line-height: 1.32; color: var(--color-ink); text-wrap: pretty;
}
.caption[data-tone='decline'] .caption-strip { color: var(--color-ink2); }
.caption .word { display: inline-block; white-space: pre; }
.caption[data-run='true'] .word { animation: word-in 480ms var(--ease-out) both; animation-delay: calc(160ms + var(--i) * var(--step, 120ms)); }
.caption .amt { font-style: normal; font-weight: 600; }
.caption .amt.big {
  display: block; margin: 0.08em 0 0.06em; font-style: normal; font-weight: 600;
  font-size: var(--monument); line-height: 0.95; letter-spacing: -0.035em; font-variation-settings: 'opsz' 144;
}
.caption .amt-tail { font-size: var(--caption-lg); font-weight: 400; letter-spacing: 0; }

/* Speaking bars beside the kicker */
.meter { display: inline-flex; align-items: flex-end; gap: 2px; height: 10px; color: var(--color-red); }
.meter b { width: 2px; height: 30%; border-radius: 1px; background: currentColor; opacity: 0.5; }
.meter[data-on='true'] b { animation: meter 900ms ease-in-out infinite; opacity: 1; }
.meter[data-on='true'] b:nth-child(2) { animation-delay: -300ms; }
.meter[data-on='true'] b:nth-child(3) { animation-delay: -600ms; }
.meter[data-on='true'] b:nth-child(4) { animation-delay: -150ms; }
.onair { display: inline-flex; align-items: center; gap: 8px; }
.onair[data-mode='idle'], .onair[data-mode='busy'] { color: var(--color-ink2); }

/* Announcement banner: a slim ink band under the bar */
.band {
  position: fixed; z-index: 20; top: calc(64px + env(safe-area-inset-top));
  left: 50%; transform: translateX(-50%); width: min(calc(100vw - 2 * var(--gutter)), 520px);
  padding: 12px 16px; border-radius: 14px; background: var(--color-ink); color: var(--color-ivory);
  display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 500;
  box-shadow: 0 10px 28px rgb(26 24 21 / 0.35); animation: drop 420ms var(--ease-out) both;
}
.band svg { width: 22px; height: 22px; flex: none; color: var(--color-red-on-ink); }

/* ---------- Soundbox (stage fallback) ---------- */
.soundbox { --col: var(--stage-max); min-height: 100dvh; display: grid; grid-template-rows: auto 1fr auto; }
.stage { display: grid; grid-template-rows: auto 1fr auto; gap: 18px; padding: 26px var(--gutter) calc(14px + env(safe-area-inset-bottom)); max-width: var(--stage-max); width: 100%; margin: 0 auto; }
.stage-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 22px; }
.stage-body { display: grid; align-content: center; gap: 14px; }
.answers { display: grid; grid-template-columns: 1.6fr 1fr; gap: 8px; }
.tools-row { display: flex; align-items: center; justify-content: center; gap: 22px; }
.tool { display: flex; align-items: center; gap: 6px; color: var(--color-ink3); font-size: 11px; }
.round { width: 30px; height: 30px; border-radius: 50%; border: 1px solid var(--color-dock-line); background: transparent; color: var(--color-ivory); display: grid; place-items: center; cursor: pointer; }
.round svg { width: 15px; height: 15px; }
.round[data-on='true'] { background: var(--color-red-on-ink); border-color: transparent; animation: pulse 1.4s var(--ease-out) infinite; }
.round:disabled, .round[aria-disabled='true'] { opacity: 0.4; cursor: not-allowed; }
.round:has(input:focus-visible) { outline: 2px solid var(--color-red-on-ink); outline-offset: 3px; }
.slate { display: none; }

.qr { display: grid; grid-template-columns: 104px 1fr; gap: 14px; align-items: center; padding: 12px; background: #fff; border: 1px solid var(--color-rule); border-radius: var(--radius-card); }
.qr img { width: 104px; height: 104px; display: block; }
.qr strong { display: block; font-size: 14px; font-weight: 600; }
.qr .qr-amt { display: block; font-family: var(--font-display); font-weight: 600; font-size: 34px; line-height: 1; letter-spacing: -0.03em; margin: 4px 0; }
.qr a { color: var(--color-ink2); font-size: 12px; }

/* Gate: the shop is closed until the first tap */
.gate { --col: var(--stage-max); min-height: 100dvh; display: grid; grid-template-rows: 1fr auto; padding: 0 var(--gutter) calc(14px + env(safe-area-inset-bottom)); }
.gate-mark { align-self: center; max-width: var(--stage-max); width: 100%; margin: 0 auto; display: grid; gap: 10px; }
.gate-mark h1 { margin: 0; font-family: var(--font-display); font-weight: 600; font-size: clamp(72px, 22vw, 120px); line-height: 0.95; letter-spacing: -0.03em; font-variation-settings: 'opsz' 144; }
.gate-mark p { margin: 0; font-family: var(--font-display); font-style: italic; font-size: 20px; max-width: 26ch; }
.gate-cta { max-width: var(--stage-max); width: 100%; margin: 0 auto; }
.gate-cta small { display: block; text-align: center; color: var(--color-ink3); font-size: 12px; margin-top: 2px; }

/* Ticker: the week's money in, an ink strip at the foot of tablet and projector screens */
.ticker { display: none; }
@media (min-width: 768px) {
  .ticker { display: flex; height: 52px; background: var(--color-ink); color: var(--color-ivory); overflow: hidden; }
  .ticker-label { flex: none; display: flex; align-items: center; padding: 0 20px; color: var(--color-red-on-ink); font-weight: 700; font-size: 11px; letter-spacing: calc(var(--type-kicker-tracking) * 1em); text-transform: uppercase; }
  .ticker-track { flex: 1; overflow: hidden; display: flex; align-items: center; }
  .ticker-run { display: flex; gap: 48px; padding-left: 48px; white-space: nowrap; width: max-content; font-size: 16px; animation: crawl var(--crawl, 30s) linear infinite; }
  .ticker-run b { font-family: var(--font-display); font-weight: 600; margin-right: 6px; }
}

/* ---------- Working screens: ledger, control, impact ---------- */
.shell { --col: 720px; min-height: 100dvh; }
.wrap { max-width: 720px; margin: 0 auto; padding: 0 var(--gutter); }
.masthead { padding: 34px 0 28px; border-bottom: 1px solid var(--color-ink); }
.masthead h1 { margin: 0; font-family: var(--font-display); font-weight: 400; font-size: var(--caption-lg); line-height: 1.2; color: var(--color-ink2); text-wrap: balance; }
.masthead p { margin: 8px 0 0; color: var(--color-ink2); font-size: 14px; }
.big-amt { display: block; margin-top: 8px; font-family: var(--font-display); font-weight: 600; font-size: var(--monument); line-height: 0.95; letter-spacing: -0.035em; font-variation-settings: 'opsz' 144; }
.big-amt.long { font-size: clamp(40px, 11vw, 112px); overflow-wrap: anywhere; }
.section-title { margin: 28px 0 4px; }
.empty { padding: 24px 0; color: var(--color-ink2); }

.rows { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--color-ink); }
.rows li { display: grid; grid-template-columns: 1fr auto; gap: 2px 16px; padding: 14px 0; border-bottom: 1px solid var(--color-rule); }
.rows .label { font-size: 16px; font-weight: 500; }
.rows .meta { grid-column: 1; color: var(--color-ink2); font-size: 13px; display: flex; gap: 12px; flex-wrap: wrap; }
.rows .kind { color: var(--color-red); font-weight: 600; font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; }
.rows .amt { grid-row: 1 / span 2; grid-column: 2; align-self: center; text-align: right; font-family: var(--font-display); font-weight: 600; font-size: 22px; }
.rows .amt.plain { color: var(--color-ink2); }

/* Ledger score strip: three columns of a ledger */
.score { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 22px; border-top: 1px solid var(--color-ink); }
.score-head { grid-column: 1 / -1; padding: 10px 0 2px; }
.score-cell { display: grid; gap: 2px; padding: 8px 14px 4px 0; }
.score-cell + .score-cell { border-left: 1px solid var(--color-rule); padding-left: 14px; }
.score-cell span { font-size: 13px; color: var(--color-ink2); }
.score-cell b { font-family: var(--font-display); font-weight: 600; font-size: 30px; line-height: 1.05; letter-spacing: -0.02em; }

/* Control */
.control { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--color-rule); }
.control h2 { margin: 0; font-family: var(--font-display); font-weight: 600; font-size: 19px; line-height: 1.2; }
.control p { margin: 2px 0 0; color: var(--color-ink2); font-size: 14px; }
.control .btn { min-height: 44px; font-size: 15px; padding: 0 18px; }
.control input, .control select { width: 104px; min-height: 42px; padding: 0 12px; border-radius: 10px; border: 1px solid var(--color-rule); background: rgb(255 255 255 / 0.6); }
.control select { appearance: none; padding-right: 30px; background: rgb(255 255 255 / 0.6) url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%235E5A52' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E") no-repeat right 8px center / 18px; }
.control .inline { display: flex; gap: 8px; align-items: center; }
.control img { background: #fff; border: 1px solid var(--color-rule); border-radius: 8px; padding: 4px; }
.log { list-style: none; margin: 8px 0 32px; padding: 0; font-size: 14px; color: var(--color-ink2); }
.log li { display: grid; grid-template-columns: auto 1fr; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-rule); }
.log li b { color: var(--color-ink); font-weight: 600; }
.log li[data-fail='true'] b { color: var(--color-red); }

/* Impact */
.picker { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 18px; }
.picker button { min-height: 36px; padding: 0 14px; border-radius: var(--radius-pill); border: 1px solid var(--color-ink); background: transparent; cursor: pointer; font-size: 14px; font-weight: 600; }
.picker button[aria-pressed='true'] { background: var(--color-ink); color: var(--color-ivory); }
.impact-head { display: grid; grid-template-columns: 1fr auto auto; gap: 16px; padding: 10px 0 6px; }
.impact li { grid-template-columns: 1fr auto; align-items: baseline; }
.impact li .label { grid-column: 1 / -1; }
.impact li .amt { grid-row: auto; grid-column: auto; }
.impact li .here { font-size: 15px; color: var(--color-ink2); text-align: left; }
@media (min-width: 640px) {
  .impact li { grid-template-columns: 1fr auto auto; }
  .impact li .label { grid-column: auto; }
  .impact li .here { text-align: right; }
}
@media (max-width: 639px) { .impact-head { grid-template-columns: 1fr auto; } .impact-head span:first-child { display: none; } }
.mix { margin-top: 18px; display: grid; gap: 8px; }
.mix-bar { display: flex; gap: 4px; height: 10px; }
.mix-bar i { background: var(--color-ink); border-radius: 2px; min-width: 6px; transform-origin: left; animation: mix-in 900ms var(--ease-out) both; }
.mix-bar i:nth-child(2) { animation-delay: 120ms; opacity: 0.75; }
.mix-bar i:nth-child(3) { animation-delay: 240ms; opacity: 0.5; }
.mix-legend { display: flex; flex-wrap: wrap; gap: 6px 20px; font-size: 14px; color: var(--color-ink2); }
.mix-legend b { font-family: var(--font-display); font-weight: 600; font-size: 16px; color: var(--color-ink); }
.footnote { color: var(--color-ink2); font-size: 13px; padding: 18px 0 40px; max-width: 60ch; }

/* ---------- Customer pay page ---------- */
.paper { --col: 720px; min-height: 100dvh; }
.paper .masthead .wrap { position: relative; }
.paper .masthead h1 { color: var(--color-ink); font-weight: 600; }
.paper .pay-body { padding-top: 24px; padding-bottom: 40px; display: grid; gap: 18px; }
.pay-body .btn-ask { min-height: 60px; }
.result { display: flex; gap: 14px; align-items: flex-start; padding-top: 16px; border-top: 1px solid var(--color-rule); }
.result svg { width: 28px; height: 28px; flex: none; margin-top: 2px; }
.result h2 { margin: 0; font-family: var(--font-display); font-weight: 600; font-size: var(--caption-sm); line-height: 1.2; }
.result p { margin: 4px 0 0; color: var(--color-ink2); }
.result[data-tone='decline'] svg { color: var(--color-red); }
.fine { color: var(--color-ink3); font-size: 13px; text-align: center; }
.facts { margin: 0; display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; font-size: 15px; }
.facts dt { color: var(--color-ink2); }
.facts dd { margin: 0; text-align: right; font-weight: 600; }

/* The "Chuka diya" ledger stamp, shared by the pay page */
.stamp {
  position: absolute; right: var(--gutter); top: -8px; width: 92px; height: 92px; border-radius: 50%;
  border: 2.5px solid var(--color-red); color: var(--color-red);
  box-shadow: inset 0 0 0 4px var(--color-paper), inset 0 0 0 5.5px var(--color-red);
  display: grid; place-content: center; text-align: center; transform: rotate(-12deg); opacity: 0.92;
  font-weight: 800; font-size: 10px; letter-spacing: 0.14em; text-transform: uppercase; line-height: 1.1;
  animation: stamp 600ms cubic-bezier(.2, .9, .3, 1.2) both;
}
.stamp em { display: block; font-family: var(--font-display); font-style: italic; font-weight: 600; font-size: 18px; letter-spacing: 0; text-transform: none; }

@keyframes word-in { from { opacity: 0.15; transform: translateY(0.14em); } }
@keyframes meter { 0%, 100% { height: 25%; } 50% { height: 100%; } }
@keyframes pulse { 0% { box-shadow: 0 0 0 0 rgb(179 38 30 / 0.45); } 70%, 100% { box-shadow: 0 0 0 8px rgb(179 38 30 / 0); } }
@keyframes drop { from { opacity: 0; transform: translate(-50%, -12px); } }
@keyframes crawl { from { transform: translateX(0); } to { transform: translateX(-50%); } }
@keyframes mix-in { from { transform: scaleX(0); } }
@keyframes stamp { 0% { transform: rotate(-12deg) scale(1.6); opacity: 0; } 70% { transform: rotate(-12deg) scale(0.96); opacity: 0.95; } 100% { transform: rotate(-12deg) scale(1); opacity: 0.92; } }

@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation: none !important; transition: none !important; } }
@media (min-width: 768px) { :root { --gutter: 32px; } .stage { padding-top: 40px; } }
/* Projector: the fallback Soundbox fills the wall */
@media (min-width: 1024px) {
  :root { --stage-max: 980px; --caption-lg: clamp(30px, 2.8vw, 44px); --monument: clamp(96px, 9vw, 150px); }
  .dock { max-width: 620px; width: 100%; margin: 0 auto; }
  .qr { max-width: 620px; margin: 0 auto; }
}
```

- [ ] **Step 2: Build**

Run: `npm run build`
Expected: `✓ built`, no CSS warnings.

- [ ] **Step 3: Commit**

```bash
git add web/styles.css
git commit -m "feat(web): ledger page and ink dock stylesheet on design tokens"
```

---

### Task 3: Markup touches — wordmark, raised ₹, dock, stamp, QR ink

**Files:**
- Modify: `web/ui.tsx:17-27` (Wordmark), `web/ui.tsx:97-99` (TestCard removed), `web/ui.tsx:135-137` (Caption ₹)
- Modify: `web/Soundbox.tsx` (gate, dock wrapper, QR colour)
- Modify: `web/Pay.tsx` (stamp)

**Interfaces:**
- Produces: `<span className="rupee">₹</span>` markup inside caption amounts and `.big-amt` figures; `.dock` wrapper around the Soundbox answers and tools; `.stamp` element on the pay page when paid.

- [ ] **Step 1: Wordmark**

In `web/ui.tsx`, replace `Wordmark` with:

```tsx
export function Wordmark() {
  return <span className="wordmark">Munshi<i aria-hidden="true" /></span>;
}
```

and delete the `TestCard` function.

- [ ] **Step 2: Raised ₹ in captions**

In `Caption`, replace the amount render

```tsx
                {m ? <>{m[1]}{m[2] && <span className="amt-tail">{m[2]}</span>}</> : part}
```

with

```tsx
                {m ? <><span className="rupee">₹</span>{m[1].slice(1)}{m[2] && <span className="amt-tail">{m[2]}</span>}</> : part}
```

and add a shared helper at the end of `web/ui.tsx`:

```tsx
// Large rupee figures: small raised ₹ beside the numerals, as on the app and the deck.
export function Rupees({ value }: { value: string }) {
  return value.startsWith('₹') ? <><span className="rupee">₹</span>{value.slice(1)}</> : <>{value}</>;
}
```

- [ ] **Step 3: Use `Rupees` in the big figures**

- `web/Ledger.tsx:47`: `{data ? <Rupees value={rupees(total)} /> : '₹—'}`
- `web/Impact.tsx:52`: `{data ? <Rupees value={rupees(income)} /> : '₹—'}`
- `web/Pay.tsx:45`: `{link ? <Rupees value={rupees(link.amount)} /> : '₹—'}`

Add `Rupees` to each file's `./ui.tsx` import (Pay already imports from `./ui.tsx`).

- [ ] **Step 4: Soundbox gate, dock and QR colour**

In `web/Soundbox.tsx`:

- Remove `TestCard` from the `./ui.tsx` import.
- Gate: replace the `gate-mark` block with

```tsx
        <div className="gate-mark">
          <span className="kicker">Ramesh Kirana · Aliganj</span>
          <h1>Munshi</h1>
          <p>Paise ka kaam bataata hai, aapki haan par karta hai.</p>
        </div>
```

and wrap the gate button in the dock:

```tsx
        <div className="gate-cta">
          <div className="dock">
            <button className="btn btn-ask btn-wide" autoFocus onClick={() => { chime(); setStarted(true); void runSession({ greet: true, scope: 'brief', max: 3 }); }}>
              Dukaan kholo
            </button>
            <small>Tap karte hi Munshi bolna shuru karega.</small>
          </div>
        </div>
```

- Main stage: keep the QR block where it is, and wrap the `answers` div, the `heard` line and the `tools-row` in one dock. Replace the stage's last `<div>` (from `{qr && pay && (` to the closing `</div>` after `tools-row`) with:

```tsx
        <div style={{ display: 'grid', gap: 12 }}>
          {qr && pay && (
            <div className="qr">
              <img src={qr} alt={`${pay.name ?? 'Grahak'} ke liye bhugtaan QR`} />
              <div>
                <strong>{pay.name ? `${pay.name}, scan karke chukayein` : 'Scan karke chukayein'}</strong>
                {pay.amount !== undefined && <span className="qr-amt num"><Rupees value={rupees(pay.amount)} /></span>}
                <a href={pay.url} target="_blank" rel="noreferrer">Link {ref}</a>
              </div>
            </div>
          )}
          <div className="dock">
            <div className="heard" aria-live="polite">{heard && (heard.startsWith('Sunaai') ? heard : <>Aapne kaha: <q>{heard}</q></>)}</div>
            <div className="answers">
              <button className="btn btn-ask" disabled={!pending} onClick={() => answer({ kind: 'yes' })}><CheckIcon />Haan</button>
              <button className="btn btn-ghost" disabled={!pending} onClick={() => answer({ kind: 'no' })}>Nahi</button>
            </div>
            <div className="tools-row">
              <div className="tool">
                <button className="round" data-on={listening} disabled={!canListen() || busy} onClick={onMic} aria-label="Bolkar poochhiye"><MicIcon /></button>
                <span>{canListen() ? 'Boliye' : 'Mic nahi'}</span>
              </div>
              <div className="tool">
                <label className="round" aria-disabled={busy}>
                  <CameraIcon />
                  <input className="sr-only" type="file" accept="image/*" capture="environment" disabled={busy}
                    aria-label="Shelf ki photo bhejiye" onChange={e => { void onPhoto(e.target.files?.[0]); e.target.value = ''; }} />
                </label>
                <span>Shelf photo</span>
              </div>
            </div>
          </div>
        </div>
```

and delete the old `heard` div from `stage-body` (it now lives in the dock). Add `Rupees` to the `./ui.tsx` import; `CrossIcon` is no longer used — remove it from the import.

- QR ink: change `color: { dark: '#141d5c', light: '#ffffff' }` to `color: { dark: '#1A1815', light: '#ffffff' }`.

- [ ] **Step 5: Pay page stamp**

In `web/Pay.tsx`, inside the masthead `wrap` div, after the `big-amt` span, add:

```tsx
          {done && <span className="stamp" aria-label="Chuka diya">Chuka<em>diya</em>{new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</span>}
```

- [ ] **Step 6: Type-check, test, build**

Run: `npm run typecheck && npm test && npm run build`
Expected: tsc silent; all server tests pass; `✓ built`.

- [ ] **Step 7: Commit**

```bash
git add web/ui.tsx web/Soundbox.tsx web/Pay.tsx web/Ledger.tsx web/Impact.tsx
git commit -m "feat(web): wordmark, raised rupee, ink dock and paid stamp"
```

---

### Task 4: Visual check of every surface

**Files:** none changed unless a defect is found.

- [ ] **Step 1: Start the server**

Run: `npm run build && npm run server` (leave running).

- [ ] **Step 2: Walk the surfaces at phone and desktop width**

Open each in a browser at 375 × 812 and at 1280 × 800, and screenshot:

1. `/soundbox` gate → tap **Dukaan kholo** → first question → **Haan** (QR card appears above the dock).
2. `/pay/<ref>` from that QR link (the link text under the QR) → **Pay** → paid state with the stamp.
3. `/ledger` after the payment: total with raised ₹, score strip, rows.
4. `/paytm`: big projected figure, mix bar in ink, picker pills.
5. `/control`: rows, pairing QR on white, log.

For each screenshot check against `docs/design/soundbox-app-mockup.html` and spec §3:
- paper with faint rules and the red margin line just left of the content;
- Fraunces for names and figures, Inter for labels; ₹ small and raised on big figures;
- red only on kickers, Haan, stamp and live dots; money figures in ink;
- no text overflow at 375 px; the long impact figure wraps instead of overflowing;
- the dock on `/soundbox` reads as one ink panel with Haan wider than Nahi.

Fix any defect in `web/styles.css` (or the markup from Task 3), rebuild, and re-check that screen.

- [ ] **Step 3: Reduced motion**

With the OS (or DevTools) set to reduce motion, reload `/soundbox` and `/pay/<ref>` after paying.
Expected: no caption word animation, no stamp drop; everything still visible.

- [ ] **Step 4: Commit any fixes**

```bash
git add web/
git commit -m "fix(web): re-skin polish from the visual pass"
```

(Skip the commit if nothing changed.)
