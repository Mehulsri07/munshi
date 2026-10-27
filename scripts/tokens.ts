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

const kebab = (s: string) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/([A-Z])([A-Z][a-z])/g, '$1-$2').toLowerCase();
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
