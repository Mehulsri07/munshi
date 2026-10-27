# Onboarding and counter-screen redesign: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merchant onboarding that personalises the demo shop, and a calmer counter screen that shows only
what the merchant can do now, both matching the approved Figma file.

**Architecture:**
- **Server:** a new `profile` table, kept across demo resets, and three endpoints. Facts and the WhatsApp
  text use the saved names.
- **Android:**
  - `Profile` API calls
  - an `Onboarding` state holder backed by a DataStore
  - Conductor additions: a QR stack, shelf lines, the asked question, khata, remind/relink/closeShop/voiceCheck
  - new Compose components drawn from the Figma components
  - `MainContent` rewritten around six states, plus three sheets and an eight-step `OnboardingFlow`

**Tech stack:**
- Server: Node 22+ `--experimental-strip-types`, `node:test`, `node:sqlite`.
- Android: Kotlin 2.3.0, Compose Material 3, DataStore preferences, Paparazzi 2.0.0-alpha04, JUnit 4,
  kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-26-onboarding-and-ui-redesign-design.md`.
**Figma:** https://www.figma.com/design/W68m8BYgxNJRF7sy8u81s1.

## Global constraints

- Work on `feat/on-device-ai`; never commit to `master`; do not push.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Gradle runs with `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` (JBR 21); from Git Bash:
  `cd /c/Project/munshi/app/android && ./gradlew.bat -q …`.
- Server tests: `cd /c/Project/munshi/app && npm test`.
- Colours only through `Tokens`; translucent tints as `Tokens.X.copy(alpha = …)`.
- Spoken lines are `Line(roman, devanagari)`. Screen text is Roman Hinglish.
- Every screen pads to `WindowInsets.safeDrawing` (Android 15 edge to edge).
- Phone layout is designed at 412 × 915 dp; the wide (tablet, landscape) branch of `MainContent` stays.
- Paparazzi goldens are recorded with `./gradlew.bat -q :app:recordPaparazziDebug` only after comparing by
  eye with the Figma frame of the same name.

---

### Task 1: Server profile

**Files:**
- Create: `server/profile.ts`, `server/test/profile.test.ts`
- Modify: `server/db.ts` (SCHEMA), `server/app.ts` (routes), `server/facts.ts`, `server/actions.ts:20`

**Interfaces:**
- Produces:
  - `type Profile = { shopName, locality, category, ownerName, addressAs: string; phone: string | null }`
  - `getProfile(db, merchantId): Profile`
  - `saveProfile(db, merchantId, input, at): Profile` (throws `ProfileError('bad_profile')`)
  - `normalisePhone(raw): string | null`
  - HTTP:
    - `POST /api/onboard/lookup {phone}` → `{profile}`, or 400 `bad_phone`
    - `GET /api/merchant` → `{profile}`
    - `POST /api/merchant {…}` → `{profile}`, or 400 `bad_profile`
  - `getFacts()` adds `addressAs`

- [ ] **Step 1: Write the failing tests** in `server/test/profile.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { openDb } from '../db.ts';
import { seed, HERO_ID } from '../seed.ts';
import { createApp } from '../app.ts';
import { getFacts } from '../facts.ts';
import { getProfile, saveProfile, normalisePhone, ProfileError } from '../profile.ts';
import { AT, fresh, deps, sent } from './helpers.ts';

const mine = { shopName: 'Gupta General Store', locality: 'Hazratganj, Lucknow', category: 'Kirana', ownerName: 'Suresh Gupta', addressAs: 'Suresh bhai', phone: '98765 43210' };

test('with nothing saved, the profile is the seeded shop', () => {
  assert.deepEqual(getProfile(fresh(), HERO_ID), { shopName: 'Ramesh Kirana', locality: 'Aliganj, Lucknow', category: 'kirana', ownerName: 'Ramesh Gupta', addressAs: 'Ramesh ji', phone: null });
});

test('phone numbers: ten digits, with or without +91, spaces or dashes', () => {
  assert.equal(normalisePhone('98765 43210'), '+919876543210');
  assert.equal(normalisePhone('+91-98765-43210'), '+919876543210');
  assert.equal(normalisePhone('919876543210'), '+919876543210');
  for (const bad of ['12345', '98765432101', 'abc', 42, null]) assert.equal(normalisePhone(bad), null);
});

test('a saved profile is read back, survives a demo reset, and names the shop in facts and reminders', async () => {
  const db = fresh();
  assert.equal(saveProfile(db, HERO_ID, mine, AT).phone, '+919876543210');
  seed(db, AT);                                            // what /api/control/reset does
  assert.equal(getProfile(db, HERO_ID).shopName, 'Gupta General Store');
  const f = getFacts(db, HERO_ID, AT);
  assert.equal(f.shop, 'Gupta General Store');
  assert.equal(f.addressAs, 'Suresh bhai');
});

test('a profile without a shop name or a name to call the merchant, or with a long field, is refused', () => {
  const db = fresh();
  for (const bad of [{ ...mine, shopName: ' ' }, { ...mine, addressAs: '' }, { ...mine, locality: 'x'.repeat(61) }, { ...mine, phone: '123' }])
    assert.throws(() => saveProfile(db, HERO_ID, bad, AT), ProfileError);
});

test('the phone API: lookup, read, save, and the WhatsApp text uses the saved shop name', async () => {
  const db = fresh(); sent.length = 0;
  const server = createApp(db, deps).listen(0);
  await new Promise(r => server.once('listening', r));
  const base = `http://localhost:${(server.address() as AddressInfo).port}`;
  const post = (p: string, body: unknown) => fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.equal((await post('/api/onboard/lookup', { phone: '12' })).status, 400);
    const found = await (await post('/api/onboard/lookup', { phone: '9876543210' })).json();
    assert.equal(found.profile.shopName, 'Ramesh Kirana');
    assert.equal(found.profile.phone, '+919876543210');
    assert.equal((await post('/api/merchant', { ...mine, shopName: '' })).status, 400);
    const saved = await (await post('/api/merchant', mine)).json();
    assert.equal(saved.profile.addressAs, 'Suresh bhai');
    assert.equal((await (await fetch(base + '/api/merchant')).json()).profile.shopName, 'Gupta General Store');
    const facts = await (await fetch(base + '/api/facts')).json();
    const sharma = facts.khata.find((k: any) => k.name === 'Sharma ji');
    const p = await (await post('/api/propose', { kind: 'udhaar_reminder', khataId: sharma.khataId })).json();
    await post(`/api/opportunities/${p.opportunityId}/confirm`, {});
    assert.match(sent.at(-1)![1], /Gupta General Store se yaad dilaana tha/);
  } finally { server.close(); }
});

test('a database file from before profiles opens', () => {
  const db = openDb(':memory:');
  db.exec('DROP TABLE profile');
  db.close();
  assert.doesNotThrow(() => { const d = openDb(':memory:'); seed(d, AT); getProfile(d, HERO_ID); });
});
```

- [ ] **Step 2: Run** `npm test`. Expected: FAIL, `Cannot find module '../profile.ts'`.

- [ ] **Step 3: Add the table** to the `SCHEMA` string in `server/db.ts`, after `merchants`. It must not go
  into `TABLES` in `server/seed.ts`, so a reset keeps it:

```sql
CREATE TABLE IF NOT EXISTS profile (
  merchant_id TEXT PRIMARY KEY, shop_name TEXT NOT NULL, locality TEXT NOT NULL, category TEXT NOT NULL,
  owner_name TEXT NOT NULL, address_as TEXT NOT NULL, phone TEXT, updated_at TEXT NOT NULL
);
```

- [ ] **Step 4: Create `server/profile.ts`:**

```ts
import type { DB } from './db.ts';

export type Profile = { shopName: string; locality: string; category: string; ownerName: string; addressAs: string; phone: string | null };
export class ProfileError extends Error {}

// The seeded hero shop until the merchant confirms their own details in onboarding.
const DEFAULT_OWNER = 'Ramesh Gupta';
const DEFAULT_ADDRESS = 'Ramesh ji';
const MAX = 60;

export function getProfile(db: DB, merchantId: string): Profile {
  const p = db.prepare('SELECT * FROM profile WHERE merchant_id=?').get(merchantId) as any;
  if (p) return { shopName: p.shop_name, locality: p.locality, category: p.category, ownerName: p.owner_name, addressAs: p.address_as, phone: p.phone ?? null };
  const m = db.prepare('SELECT name, locality, category FROM merchants WHERE id=?').get(merchantId) as any;
  return { shopName: m.name, locality: m.locality ?? '', category: m.category, ownerName: DEFAULT_OWNER, addressAs: DEFAULT_ADDRESS, phone: null };
}

// Ten digits, with or without +91, spaces or dashes; returned as +91XXXXXXXXXX.
export function normalisePhone(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const d = raw.replace(/[\s-]/g, '').replace(/^\+?91(?=\d{10}$)/, '');
  return /^\d{10}$/.test(d) ? '+91' + d : null;
}

export function saveProfile(db: DB, merchantId: string, input: any, at: Date): Profile {
  const s = (k: string) => (typeof input?.[k] === 'string' ? input[k].trim() : '');
  const phone = input?.phone == null || input.phone === '' ? null : normalisePhone(input.phone);
  const p: Profile = { shopName: s('shopName'), locality: s('locality'), category: s('category'), ownerName: s('ownerName'), addressAs: s('addressAs'), phone };
  const tooLong = [p.shopName, p.locality, p.category, p.ownerName, p.addressAs].some(v => v.length > MAX);
  if (!p.shopName || !p.addressAs || tooLong || (input?.phone && !phone)) throw new ProfileError('bad_profile');
  db.prepare(`INSERT INTO profile (merchant_id,shop_name,locality,category,owner_name,address_as,phone,updated_at) VALUES (?,?,?,?,?,?,?,?)
    ON CONFLICT(merchant_id) DO UPDATE SET shop_name=excluded.shop_name, locality=excluded.locality, category=excluded.category,
      owner_name=excluded.owner_name, address_as=excluded.address_as, phone=excluded.phone, updated_at=excluded.updated_at`)
    .run(merchantId, p.shopName, p.locality, p.category, p.ownerName, p.addressAs, p.phone, at.toISOString());
  return p;
}
```

- [ ] **Step 5: Wire it into the server:**
  - **`server/facts.ts`:**
    - import `getProfile`
    - replace the `shop` line with `const profile = getProfile(db, merchantId); const shop = profile.shopName;`
    - return `{ shop, addressAs: profile.addressAs, khata, tasks, week, inventory }`
  - **`server/actions.ts`:**
    - import `getProfile`
    - in `sendUdhaarReminder`, before `body`, add `const shop = getProfile(db, opp.merchantId).shopName;`
    - replace `Ramesh Kirana se` with `${shop} se`
  - **`server/app.ts`:**
    - import `{ getProfile, saveProfile, normalisePhone, ProfileError } from './profile.ts'`
    - add these routes after the `/api/facts` route:

```ts
  ['POST', /^\/api\/onboard\/lookup$/, ({ db, body }) => {
    // Simulated Paytm for Business lookup: any valid number "finds" the demo shop.
    const phone = normalisePhone(body.phone);
    if (!phone) throw new HttpError(400, 'bad_phone');
    return { profile: { ...getProfile(db, HERO_ID), phone } };
  }],
  ['GET', /^\/api\/merchant$/, ({ db }) => ({ profile: getProfile(db, HERO_ID) })],
  ['POST', /^\/api\/merchant$/, ({ db, body }) => {
    try { return { profile: saveProfile(db, HERO_ID, body, now()) }; }
    catch (e) { if (e instanceof ProfileError) throw new HttpError(400, e.message); throw e; }
  }],
```

- [ ] **Step 6: Run** `npm test`. Expected: all pass, including the existing `phone-api` facts test
  (`shop` is still `Ramesh Kirana` by default).

- [ ] **Step 7: Commit**

```bash
git add server/profile.ts server/test/profile.test.ts server/db.ts server/app.ts server/facts.ts server/actions.ts
git commit -m "feat(server): merchant profile from onboarding, kept across demo resets"
```

---

### Task 2: Android API for the profile

**Files:**
- Modify: `android/app/src/main/java/com/opuscube/munshi/api/Models.kt`, `api/MunshiApi.kt`,
  `api/HttpMunshiApi.kt`, `brain/Guards.kt` (`parseShelf` keeps `count`)
- Create: `android/app/src/test/resources/fixtures/profile.json`
- Test: `api/ContractTest.kt`, `api/HttpMunshiApiTest.kt`, `conductor/Fakes.kt`, `brain/GuardsTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Profile(shopName: String, locality: String = "", category: String = "", ownerName: String = "", addressAs: String, phone: String? = null)`
  - `MunshiApi.lookup(phone: String): Profile`
  - `MunshiApi.merchant(): Profile`
  - `MunshiApi.saveMerchant(p: Profile): Profile`
  - `Facts.addressAs: String?`
  - `Reading(sku, level, count: Int? = null)`

- [ ] **Step 1: Write the failing tests.**
  - The fixture `profile.json`:
    `{"profile":{"shopName":"Ramesh Kirana","locality":"Aliganj, Lucknow","category":"kirana","ownerName":"Ramesh Gupta","addressAs":"Ramesh ji","phone":"+919876543210"}}`
  - In `ContractTest`:

```kotlin
@Test fun `profile replies carry the names Munshi uses`() {
    val p = MunshiJson.decodeFromString<ProfileReply>(fixture("profile.json")).profile
    assertEquals("Ramesh ji", p.addressAs)
    assertEquals("+919876543210", p.phone)
}
```

  - In `HttpMunshiApiTest`, using its existing MockWebServer pattern, assert:
    - `saveMerchant` POSTs to `/api/merchant` with `"addressAs":"Suresh bhai"` in the body
    - `lookup("98765 43210")` POSTs `{"phone":"98765 43210"}` to `/api/onboard/lookup`
  - In `GuardsTest`: `parseShelf("""[{"sku":"surf_1kg","count":2}]""", facts.inventory)` equals
    `listOf(Reading("surf_1kg", "low", 2))`.

- [ ] **Step 2: Run** `./gradlew.bat -q :app:testDebugUnitTest`. Expected: compile failure, `Unresolved
  reference: ProfileReply`.

- [ ] **Step 3: Implement.**
  - **`Models.kt`:**

```kotlin
@Serializable data class Profile(val shopName: String, val locality: String = "", val category: String = "", val ownerName: String = "", val addressAs: String, val phone: String? = null)
@Serializable data class ProfileReply(val profile: Profile)
```

    - add `val addressAs: String? = null` to `Facts` (after `shop`)
    - change `Reading` to `@Serializable data class Reading(val sku: String, val level: String, val count: Int? = null)`
  - **`MunshiApi`:** add `suspend fun lookup(phone: String): Profile`, `suspend fun merchant(): Profile`,
    `suspend fun saveMerchant(p: Profile): Profile`.
  - **`HttpMunshiApi`:**

```kotlin
override suspend fun lookup(phone: String): Profile =
    call<ProfileReply>("POST", "/api/onboard/lookup", buildJsonObject { put("phone", JsonPrimitive(phone)) }.toString()).profile
override suspend fun merchant(): Profile = call<ProfileReply>("GET", "/api/merchant").profile
override suspend fun saveMerchant(p: Profile): Profile =
    call<ProfileReply>("POST", "/api/merchant", MunshiJson.encodeToString(Profile.serializer(), p)).profile
```

  - **`Guards.kt` (`parseShelf`):** read `val count = o["count"]?.jsonPrimitive?.intOrNull` and return
    `Reading(sku, level, count)`.
  - **`Fakes.kt` (`FakeApi`):**

```kotlin
var profile = Profile("Ramesh Kirana", "Aliganj, Lucknow", "kirana", "Ramesh Gupta", "Ramesh ji")
var lookupFails = false
override suspend fun lookup(phone: String): Profile { calls += "lookup:$phone"; if (lookupFails) throw ApiError(400, "bad_phone"); return profile.copy(phone = phone) }
override suspend fun merchant(): Profile = profile.also { calls += "merchant" }
override suspend fun saveMerchant(p: Profile): Profile { calls += "save:${p.addressAs}"; profile = p; return p }
```

- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat(android): profile API, shelf counts kept`.

---

### Task 3: Tokens, icons and the shared components

**Files:**
- Modify: `design/tokens.json`, then regenerate `ui/theme/Tokens.kt` (and the web CSS) with `npm run tokens`
- Create:
  - `ui/components/Icons.kt`
  - `ui/components/Buttons.kt` (`MunshiButton`, `ButtonStyle`, `MicButton`)
  - `ui/components/TopBar.kt` (`Status`, `StatusDot`, `TopBar`)
  - `ui/components/Rows.kt` (`TaskRow`, `FieldRow`)
  - `ui/components/Sheet.kt` (`MunshiSheet`)
- Modify:
  - `ui/components/QrCard.kt` (three states)
  - `ui/components/WeekChip.kt` (Figma look)
  - `ui/components/LedgerPage.kt` (no ruled lines, margin line kept)
- Test: `ui/ComponentsScreenshotTest.kt`

**Interfaces:**
- Consumes `PayQr` and `QrState` from Task 4. Write Task 4's `UiState` changes first if executing in order;
  the declarations are repeated below.
- Produces:
  - `MunshiIcons.{Mic, Camera, Check, Close, Store, Phone, Bell, Spark, Qr, ChevronRight, Wifi, Speaker, Rupee}`
  - `MunshiButton(label, onClick, modifier, style = Primary, icon: ImageVector? = null, enabled = true)`
  - `MicButton(listening, onClick, modifier)`
  - `StatusDot(status)`
  - `TopBar(status, weekTotal: Int?, flash: Boolean, onBrand, onWeek)`
  - `TaskRow(kicker, who, amount)`
  - `FieldRow(label, value, onChange, accent = false, big = false, keyboard = KeyboardOptions.Default)`
  - `QrCard(qr: PayQr, today: String, onRelink: () -> Unit, modifier, animate)`
  - `QrStack(qrs: List<PayQr>, today, onRelink, animate)`
  - `MunshiSheet(onDismiss, content)`

- [ ] **Step 1: Add the four colours** to `design/tokens.json` under `color`:
  `"card": "#FFFDF8", "redSoft": "#F3DED8", "ok": "#2E7D4F", "warn": "#B7791F"`.
  Run `npm run tokens`, then `npm test`; the `tokens.test.ts` sync check must pass. `Tokens.kt` now has
  `Card`, `RedSoft`, `Ok` and `Warn`.

- [ ] **Step 2: Create `Icons.kt`.** It holds the same paths as the Figma icons, with circles and rects
  written as paths:

```kotlin
package com.opuscube.munshi.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Line icons from the Figma file (24 grid, 1.8 stroke, round caps). Tint with Icon(tint = …).
object MunshiIcons {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    val Mic = icon("Mic", rrect(9f, 2.5f, 6f, 11.5f, 3f), "M5.5 10.5v.5a6.5 6.5 0 0 0 13 0v-.5", "M12 17.5v4")
    val Camera = icon("Camera", "M4 7.5h3l1.8-2.7h6.4L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z", circle(12f, 13.5f, 3.6f))
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("Close", "M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Store = icon("Store", "M3.5 9.5L5.5 4.5h13l2 5", "M4.5 9.5V20h15V9.5", "M9.5 20v-5.5h5V20")
    val Phone = icon("Phone", rrect(7f, 2.5f, 10f, 19f, 2.2f), "M11 18h2")
    val Bell = icon("Bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2z", "M10 21h4")
    val Spark = icon("Spark", "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", "M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z")
    val Qr = icon("Qr", rrect(4f, 4f, 6f, 6f, 1f), rrect(14f, 4f, 6f, 6f, 1f), rrect(4f, 14f, 6f, 6f, 1f), "M14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 19.5h2")
    val ChevronRight = icon("ChevronRight", "M9.5 6l6 6-6 6")
    val Wifi = icon("Wifi", "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.8 16a4.8 4.8 0 0 1 6.4 0", circle(12f, 19.3f, 0.9f))
    val Speaker = icon("Speaker", "M4 9.5h3.5L12.5 5.5v13l-5-4H4z", "M16 9a4.2 4.2 0 0 1 0 6M18.6 6.5a8 8 0 0 1 0 11")
    val Rupee = icon("Rupee", "M7 4.5h10M7 8.5h10M7 4.5h3.5a4 4 0 0 1 0 8H7l8 7")
}
```

- [ ] **Step 3: Create `Buttons.kt`:**

```kotlin
package com.opuscube.munshi.ui.components

// imports: compose foundation background/border/clickable/layout, shape, material3 Icon/Text,
// ui Alignment/Modifier/draw.alpha/draw.clip/draw.shadow/graphics.Brush/vector.ImageVector,
// semantics.Role, text.font.FontWeight, unit dp/sp, ui.theme Inter/Tokens

enum class ButtonStyle { Primary, Secondary, Tool }

// Primary (red) is the one thing to do now; Secondary is Nahi; Tool is a small pill (Boliye, Shelf photo).
@Composable
fun MunshiButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.Primary, icon: ImageVector? = null, enabled: Boolean = true) {
    val tool = style == ButtonStyle.Tool
    val shape = RoundedCornerShape(if (tool) Tokens.RadiusPill.dp else Tokens.RadiusButton.dp)
    val look = when (style) {
        ButtonStyle.Primary -> Modifier.background(Brush.verticalGradient(listOf(Tokens.RedTop, Tokens.RedBottom)), shape)
        ButtonStyle.Secondary -> Modifier.border(1.5.dp, Tokens.Ivory.copy(alpha = 0.4f), shape)
        ButtonStyle.Tool -> Modifier.background(Tokens.Ivory.copy(alpha = 0.1f), shape)
    }
    Row(
        modifier.height(if (tool) 44.dp else 56.dp).alpha(if (enabled) 1f else 0.4f).clip(shape).then(look)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (tool) 14.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(if (tool) 20.dp else 22.dp), tint = Tokens.Ivory) }
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = if (tool) 14.sp else Tokens.TypeButtonSize.sp, color = Tokens.Ivory, maxLines = 1)
    }
}

// Boliye as the main action when nothing is being asked; listening adds the ring.
@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(88.dp).then(if (listening) Modifier.border(3.dp, Tokens.RedOnInk, CircleShape) else Modifier)
            .clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Boliye", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(72.dp).shadow(12.dp, CircleShape).background(Brush.verticalGradient(listOf(Tokens.RedTop, Tokens.RedBottom)), CircleShape), contentAlignment = Alignment.Center) {
            Icon(MunshiIcons.Mic, "Boliye", Modifier.size(30.dp), tint = Tokens.Ivory)
        }
    }
}
```

- [ ] **Step 4: Create `TopBar.kt`:**

```kotlin
enum class Status { Ready, Loading, Offline }

fun statusOf(live: Boolean, mind: String?): Status = when { !live -> Status.Offline; mind != null -> Status.Loading; else -> Status.Ready }

@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier) {
    val c = when (status) { Status.Ready -> Tokens.Ok; Status.Loading -> Tokens.Warn; Status.Offline -> Tokens.Ink3 }
    Box(modifier.size(10.dp).background(c, CircleShape))
}

// Wordmark and status (tap: settings) and the week chip (tap: hisaab).
@Composable
fun TopBar(status: Status, weekTotal: Int?, flash: Boolean, onBrand: () -> Unit, onWeek: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Settings", onClick = onBrand).padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Munshi", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Tokens.Ink)
            StatusDot(status)
        }
        Spacer(Modifier.weight(1f))
        weekTotal?.let { WeekChip(it, flash, onWeek) }
    }
}
```

  Restyle `WeekChip`:
  - border `Tokens.Ink.copy(alpha = 0.18f)`
  - background `Tokens.Card.copy(alpha = 0.6f)`
  - padding 14 × 8
  - text "Is hafte" in Inter SemiBold 12 sp ink2, then the amount in Inter Bold
  - keep the count-up and flash animations

- [ ] **Step 5: Create `Rows.kt`:**

```kotlin
// A line in the brief or the udhaar sheet: why (kicker), who, how much.
@Composable
fun TaskRow(kicker: String, who: String, amount: Int, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Kicker(kicker)
                Text(who, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Tokens.Ink)
            }
            Text(rupees(amount), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Tokens.Ink)
            trailing?.invoke()
        }
        HorizontalDivider(color = Tokens.Rule)
    }
}

// Onboarding field: small label, large value, one underline.
@Composable
fun FieldRow(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, big: Boolean = false, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.16.em, color = Tokens.Ink3)
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = keyboard,
            textStyle = TextStyle(fontFamily = if (accent) Fraunces else Inter, fontStyle = if (accent) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (big) FontWeight.Medium else FontWeight.Normal, fontSize = if (big) 26.sp else 18.sp, color = if (accent) Tokens.Red else Tokens.Ink),
            cursorBrush = SolidColor(Tokens.Red))
        HorizontalDivider(thickness = 1.5.dp, color = Tokens.Ink.copy(alpha = 0.25f))
    }
}
```

  `Kicker(text)` already exists. Its default colour is red; check `Kicker.kt` and keep it.

- [ ] **Step 6: Rewrite `QrCard.kt` for the three states, and add `QrStack`.** Keep `qrBitmap`.

```kotlin
@Composable
fun QrCard(qr: PayQr, today: String, onRelink: () -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Row(modifier.shadow(6.dp, shape, ambientColor = Tokens.Ink.copy(alpha = 0.1f)).background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            val img = remember(qr.url) { qrBitmap(qr.url).asImageBitmap() }
            Image(img, "QR ${qr.name.orEmpty()}", Modifier.size(100.dp).alpha(when (qr.state) { QrState.Open -> 1f; QrState.Paid -> 0.35f; QrState.Expired -> 0.18f }))
            if (qr.state == QrState.Paid) ChukaDiyaStamp(today, Modifier.size(92.dp), animate = animate)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val who = qr.name ?: "Grahak"
            Text(when (qr.state) { QrState.Open -> "$who, scan karke chukayein"; QrState.Paid -> "$who ne chuka diya"; QrState.Expired -> "$who ka link" },
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
            qr.amount?.let { RupeeFigure(it, size = 40.sp, color = if (qr.state == QrState.Expired) Tokens.Ink3 else Tokens.Ink) }
            when (qr.state) {
                QrState.Open -> Text("Link 1 ghante tak chalega", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
                QrState.Paid -> Text("Paise aa gaye", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ok)
                QrState.Expired -> {
                    Text("Link khatam ho gaya", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Red)
                    Text("Naya link bhejein →", Modifier.clickable(onClick = onRelink).padding(vertical = 4.dp), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Red)
                }
            }
        }
    }
}

// Every open link, newest first; the next card peeks in from the right.
@Composable
fun QrStack(qrs: List<PayQr>, today: String, onRelink: (String) -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (qrs.isEmpty()) return
    val pager = rememberPagerState { qrs.size }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Khule QR · ${qrs.count { it.state == QrState.Open }}")
            Spacer(Modifier.weight(1f))
            if (qrs.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(qrs.size) { i -> Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).background(if (i == pager.currentPage) Tokens.Ink else Tokens.Ink.copy(alpha = 0.2f), CircleShape)) }
            }
        }
        HorizontalPager(pager, contentPadding = PaddingValues(end = if (qrs.size > 1) 28.dp else 0.dp), pageSpacing = 12.dp, key = { qrs[it].ref }) { i ->
            QrCard(qrs[i], today, { onRelink(qrs[i].ref) }, Modifier.fillMaxWidth(), animate)
        }
    }
}
```

- [ ] **Step 7: Create `Sheet.kt`, and drop the ruled lines in `LedgerPage`** (keep the margin line):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MunshiSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tokens.Paper, scrimColor = Tokens.Ink.copy(alpha = 0.42f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(40.dp, 4.dp).background(Tokens.Ink.copy(alpha = 0.18f), RoundedCornerShape(2.dp))) }) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
```

- [ ] **Step 8: Screenshot the components.** In `ComponentsScreenshotTest.parts()`, replace the old buttons
  with:
  - an ink `Column` holding `MunshiButton("Haan", {}, icon = MunshiIcons.Check)`,
    `MunshiButton("Nahi", {}, style = ButtonStyle.Secondary)` and
    `MunshiButton("Boliye", {}, style = ButtonStyle.Tool, icon = MunshiIcons.Mic)`
  - `MicButton(false, {})` and `MicButton(true, {})`
  - the three `QrCard` states
  - `TaskRow("Udhaar · 12 din", "Sharma ji", 450)`
  - `FieldRow("Dukaan ka naam", "Ramesh Kirana Store", {})`
  - `TopBar(Status.Ready, 742, false, {}, {})`

  Then:
  1. Run `:app:recordPaparazziDebug`.
  2. Open `app/src/test/snapshots/images/*ComponentsScreenshotTest_parts.png` and compare it with the
     Figma Components page.
  3. Fix any mismatch.
  4. Re-record.

- [ ] **Step 9: Run** `:app:testDebugUnitTest`. This includes `verifyPaparazziDebug` if wired; if not, run it
  separately. Expected: PASS.
- [ ] **Step 10: Commit** with `feat(android): Figma components: icons, buttons, mic, top bar, QR states, rows, sheet`.

---

### Task 4: Conductor and UiState

**Files:**
- Modify: `conductor/UiState.kt`, `conductor/Conductor.kt`
- Test: `conductor/ConductorTest.kt`

**Interfaces:**
- Produces in `UiState.kt`:

```kotlin
enum class QrState { Open, Paid, Expired }
data class PayQr(val url: String, val ref: String, val name: String?, val amount: Int?, val state: QrState = QrState.Open)
data class ShelfLine(val sku: String, val name: String, val count: Int?, val level: String)
data class Ask(val id: Long, val display: Display?, val line: Line, val type: String? = null)
// UiState: payQrs: List<PayQr> = emptyList() replaces payQr; Stamp and stamp are removed;
// asked: String? = null; shelf: List<ShelfLine>? = null; khata: List<FactKhata> = emptyList(); profile: Profile? = null
```

- Conductor gains:
  - `closeShop()`
  - `remind(khataId: Long)`
  - `relink(ref: String)`
  - `suspend fun voiceCheck(name: String): String?`
  - `start()` also loads the profile and the khata
  - `GREET` becomes `greet(profile)`

- [ ] **Step 1: Write the failing tests** in `ConductorTest.kt`. These replace any test that read `payQr`
  or `stamp`:

```kotlin
@Test fun `the greeting uses the merchant's own name`() = runTest {
    api.profile = api.profile.copy(addressAs = "Suresh bhai"); api.nexts += SHARMA
    val c = conductor(); c.start(); runCurrent(); c.openShop(); runCurrent()
    assertTrue(speaker.said.first().startsWith("Suprabhat Suresh bhai!"))
    assertEquals("Suresh bhai", c.state.value.profile!!.addressAs)
}

@Test fun `pay links stack, a paid one is stamped then leaves, an expired one can be sent again`() = runTest {
    api.nexts += listOf(SHARMA, PINKY)
    api.confirmResult = { id -> if (id == 1L) ActionResult("ok", payUrl = "https://x/pay/aa11", payFor = PayFor("Sharma ji", 450)) else ActionResult("ok", payUrl = "https://x/pay/bb22", payFor = PayFor("Pinky didi", 230)) }
    val c = conductor(); c.openShop(); runCurrent()
    c.answer(Answer.Yes); runCurrent(); c.answer(Answer.Yes); runCurrent()
    assertEquals(listOf("bb22", "aa11"), c.state.value.payQrs.map { it.ref })          // newest first
    c.onEvent(SoundboxEvent.Paid("aa11", "Sharma ji", 450)); runCurrent()
    assertEquals(QrState.Paid, c.state.value.payQrs.first { it.ref == "aa11" }.state)
    advanceTimeBy(4_100); runCurrent()
    assertEquals(listOf("bb22"), c.state.value.payQrs.map { it.ref })
    c.onEvent(SoundboxEvent.Expired("bb22")); runCurrent()
    assertEquals(QrState.Expired, c.state.value.payQrs.single().state)
    c.relink("bb22"); runCurrent()
    assertTrue(c.state.value.payQrs.isEmpty())
    assertTrue(api.calls.count { it.startsWith("next") } >= 3)                        // asked the server's next task again
}

@Test fun `Yaad dilao from the udhaar sheet proposes and asks that reminder`() = runTest {
    val c = conductor(); c.remind(2); runCurrent()
    assertTrue("propose:2" in api.calls)
    assertEquals(PINKY.id, c.state.value.ask!!.id)
}

@Test fun `a shelf reading lists each item with its count until the offer is answered`() = runTest {
    val brain = FakeBrain().apply { readings = listOf(Reading("surf_1kg", "low", 2)) }
    val c = conductor(brain); c.shelf(ByteArray(3)); runCurrent()
    assertEquals(listOf(ShelfLine("surf_1kg", "Surf Excel 1kg", 2, "low")), c.state.value.shelf)
}

@Test fun `the free-speech question shows while the answer is made`() = runTest {
    val brain = FakeBrain().apply { reply = BrainReply(Line("Kul ₹680 baaki hai."), null) }
    val gate = CompletableDeferred<Unit>(); speaker.gate = gate
    val c = conductor(brain); c.hear("kitna udhaar baaki hai"); runCurrent()
    assertEquals("kitna udhaar baaki hai", c.state.value.asked)
    gate.complete(Unit); runCurrent()
    assertNull(c.state.value.asked)
}

@Test fun `the voice check says hello and returns what it heard`() = runTest {
    listener.available0 = true; listener.replies += "haan"
    val c = conductor()
    assertEquals("haan", c.voiceCheck("Suresh bhai"))
    assertTrue(speaker.said.single().startsWith("Namaste Suresh bhai, main Munshi hoon."))
}

@Test fun `start loads the khata for the tally and the udhaar sheet, and Band karo closes the shop`() = runTest {
    api.nexts += SHARMA
    val c = conductor(); c.start(); runCurrent()
    assertEquals(listOf("Pinky didi"), c.state.value.khata.map { it.name })
    c.openShop(); runCurrent(); c.answer(Answer.No); runCurrent()
    c.closeShop(); runCurrent()
    assertFalse(c.state.value.shopOpen)
}
```

- [ ] **Step 2: Run** `./gradlew.bat -q :app:testDebugUnitTest --tests '*ConductorTest*'`. Expected:
  compile failure (`Unresolved reference: payQrs`).

- [ ] **Step 3: Implement in `Conductor.kt`:**
  - **Greeting.** Remove `GREET` and add
    `private fun greet(p: Profile?) = (p?.addressAs ?: "Ramesh ji").let { Line("Suprabhat $it! Aaj ke kaam sunaata hoon.", "सुप्रभात $it! आज के काम सुनाता हूँ।") }`.
    `runSession` says `greet(_state.value.profile)`.
  - **`start()`:**
    `scope.launch { refreshProfile(); refreshBrief(); refreshLedger(); refreshKhata() }`, with
    - `private suspend fun refreshProfile() { runCatching { api.merchant() }.getOrNull()?.let { p -> _state.update { it.copy(profile = p) } } }`
    - `private suspend fun refreshKhata() { runCatching { api.facts().khata }.getOrNull()?.let { k -> _state.update { it.copy(khata = k) } } }`
    - also call `refreshKhata()` on `SoundboxEvent.LedgerChanged` and in `runSession`'s `finally`
      (launched in `this.scope`)
  - **Pay links:**
    - `show(r)`:
      `_state.update { s -> s.copy(payQrs = listOf(PayQr(url, url.substringAfterLast('/'), r.payFor?.name, r.payFor?.amount)) + s.payQrs.filter { it.ref != ref }) }`
    - `Paid` event: map the matching ref to `state = QrState.Paid`, then
      `scope.launch { delay(4_000); _state.update { s -> s.copy(payQrs = s.payQrs.filter { it.ref != e.ref }) } }`
    - `Expired` event: map the matching ref to `QrState.Expired`
    - `fun relink(ref: String) { _state.update { s -> s.copy(payQrs = s.payQrs.filter { it.ref != ref }) }; session(greet = false, scope = "all", max = 1) }`
    - `WeekChip`'s `flash` becomes `state.payQrs.any { it.state == QrState.Paid }`
  - **`fun closeShop()`:**
    `scope.launch { sessionJob?.cancelAndJoin(); pending = null; speaker.stop(); _state.update { it.copy(shopOpen = false, ask = null, mode = Mode.Brief) }; refreshBrief() }`
  - **`fun remind(khataId: Long)`:**

```kotlin
fun remind(khataId: Long) {
    if (!running.compareAndSet(false, true)) return
    scope.launch {
        try {
            val p = api.propose(khataId)
            resolve(p.opportunityId, p.display, worded(Line(p.line, p.spoken)), speak = true, type = "udhaar_overdue")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { say(OOPS) }
        finally { running.set(false); _state.update { it.copy(mode = Mode.Idle) } }
    }
}
```

  - **`resolve(…)`:**
    - gains `type: String? = null`, stored in `Ask`
    - every caller passes `n.type` / `it.type` where it has a `Next`
    - after the answer, `_state.update { it.copy(ask = null, shelf = null) }`
  - **Free speech.** In `converseAndFollow(text)` and in `resolve`'s `Answer.Text` branch:
    - set `_state.update { it.copy(asked = text) }` before `freeSpeech(text)`
    - set `asked = null` in a `finally` after it returns
  - **`shelf(jpeg)`:**
    - keep the inventory: `val inventory = api.facts().inventory`
    - after `readings` are read and not null, set
      `_state.update { s -> s.copy(shelf = readings.map { r -> ShelfLine(r.sku, inventory.firstOrNull { it.sku == r.sku }?.name ?: r.sku, r.count, r.level) }) }`
  - **`voiceCheck`:**

```kotlin
// Onboarding's last step: Munshi speaks, then listens once; null when nothing was heard.
suspend fun voiceCheck(name: String): String? {
    speaker.say(Line("Namaste $name, main Munshi hoon. Sun pa rahe hain? Haan boliye.", "नमस्ते $name, मैं मुंशी हूँ। सुन पा रहे हैं? हाँ बोलिए।"))
    return if (listener.available) listenOnce() else null
}
```

- [ ] **Step 4: Fix the other users of `payQr`/`stamp`:**
  - `MainContent` is rewritten in Task 5; until then, delete its `PaidBlock` and the stamp overlay so the
    project compiles
  - `MainContentScreenshotTest` gets the new states in Task 5
- [ ] **Step 5: Run** all unit tests. Expected: PASS. Fix any older test that asserted on `payQr`/`stamp`
  by using `payQrs`.
- [ ] **Step 6: Commit** with `feat(conductor): QR stack, shelf lines, asked question, khata, remind/relink/closeShop/voiceCheck, greeting by name`.

---

### Task 5: The counter screen

**Files:**
- Rewrite: `ui/screens/MainContent.kt`
- Modify: `ui/screens/MainScreen.kt`
- Test: `ui/MainContentScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 3 components; Task 4 `UiState`.
- Produces:
  - `MainActions(openShop, yes, no, mic, camera, week, settings, udhaar, closeShop, relink: (String) -> Unit)`
  - `MainContent(state, actions, today, modifier, animate)`

- [ ] **Step 1: Rewrite the screenshot tests** for the six Figma states (phone and tablet stay
  parameterised):

```kotlin
private val noop = MainActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
private val khata = listOf(FactKhata(1, "Sharma ji", 450, 12), FactKhata(2, "Pinky didi", 230, 9))
private val sharma = Ask(1, Display("Sharma ji ka", 450, "udhaar 12 din se baaki hai. Reminder bhej doon?"), Line("…"), type = "udhaar_overdue")
private val open = PayQr("https://m.test/pay/ab12", "ab12", "Sharma ji", 450)
private val profile = Profile("Ramesh Kirana Store", addressAs = "Ramesh ji")

@Test fun c1MorningBrief() = shot(UiState(mode = Mode.Brief, brief = brief, ledger = ledger, live = true, profile = profile))
@Test fun c2Asking() = shot(UiState(mode = Mode.Speaking, ask = sharma, ledger = ledger, live = true, shopOpen = true))
@Test fun c3Listening() = shot(UiState(mode = Mode.Listening, ask = sharma, heard = "pehle Pinky didi ko bhejo", ledger = ledger, live = true, shopOpen = true))
@Test fun c4Thinking() = shot(UiState(mode = Mode.Working, asked = "Kitna udhaar baaki hai?", ledger = ledger, live = true, shopOpen = true))
@Test fun c5Open() = shot(UiState(mode = Mode.Idle, payQrs = listOf(open, open.copy(ref = "cd34", name = "Pinky didi", amount = 230)), khata = khata, ledger = ledger, live = true, shopOpen = true))
@Test fun c6Shelf() = shot(UiState(mode = Mode.Speaking, ask = Ask(7, Display("Surf Excel 1kg group order", 143, "bachenge: 4 dukaanein, 8% bachat. Jud jaaun?"), Line("…"), "group_buy_open"),
    shelf = listOf(ShelfLine("tata_salt", "Tata Salt 1kg", 6, "ok"), ShelfLine("surf_1kg", "Surf Excel 1kg", 2, "low"), ShelfLine("parle_g", "Parle-G", 1, "low")), ledger = ledger, live = true, shopOpen = true))
@Test fun paidCard() = shot(UiState(mode = Mode.Idle, payQrs = listOf(open.copy(state = QrState.Paid)), khata = khata, ledger = ledger.copy(total = 1192), live = true, shopOpen = true))
@Test fun reconnecting() = shot(UiState(mode = Mode.Idle, ask = sharma, ledger = ledger, live = false, shopOpen = true))
@Test fun downloading() = shot(UiState(mode = Mode.Brief, brief = brief, ledger = ledger, live = true, mind = "Munshi ka dimaag aa raha hai · 1.2 / 3.7 GB"))
@Test fun banner() = shot(UiState(mode = Mode.Idle, banner = "₹120 prapt hue", khata = khata, ledger = ledger, live = true, shopOpen = true))
```

- [ ] **Step 2: Rewrite `MainContent.kt`:**
  - **Frame.** Keep the `BoxWithConstraints` wide/phone split.
  - **`Page` order:** `TopBar`, banner, then the state block:

```kotlin
when {
    s.ask != null -> AskBlock(s)                       // C2, C3 (live caption under it), C6 (shelf lines above the offer)
    s.mode == Mode.Working && s.asked != null -> ThinkingBlock(s.asked)      // C4
    !s.shopOpen -> BriefBlock(s)                       // C1
    else -> OpenBlock(s, a)                            // C5
}
```

    Then, when not in C1, `QrStack(s.payQrs, today, a.relink)`.
  - **The blocks**, each using the Figma text styles from spec §3:
    - **`BriefBlock`:**
      - date kicker in ink3 (`today` formatted as "Somvaar, 26 Oktober" by the caller)
      - "Shubh prabhat, {profile.addressAs ?: "Ramesh ji"}" in Display/Greeting
      - "Aaj ke N kaam se ₹X aa sakte hain." in Body/Regular ink2
      - `TaskRow(kickerFor(item.type), item.title, item.amount)` for each brief item
    - **`kickerFor`:**
      - `udhaar_overdue` → "Udhaar"
      - `local_demand_spike` → "Mauka"
      - `group_buy_open` → "Group order"
      - `winback` → "Grahak wapas"
      - `working_capital` → "Loan"
      - otherwise "Kaam"
    - **`AskBlock`:**
      - if `shelf != null`:
        - a "Shelf dekh li" kicker with the camera icon
        - the headline "{names with level != ok joined by aur} kam hain"
        - a card of `ShelfLine` rows: name, "N packet dikhe", and a level pill (ok: "Theek" on
          `Ok.copy(alpha=.14f)`; low: "Kam" on `Warn`; empty: "Khatam" on `Red`)
      - then the speaking kicker: `Kicker(kickerFor(ask.type), speaking = mode == Speaking)`
      - who (Title/Who 24 sp), `RupeeFigure`, the italic line in ink2, and the tags row
        ("WhatsApp reminder", "Link 1 ghante ka") only for `udhaar_overdue`
      - the hint row (Mic icon in ink3 and "Kuch aur poochna ho toh Boliye dabaiye")
      - when `mode == Listening && heard != null`: a live-caption card (Mic icon in red, the italic quote)
    - **`ThinkingBlock`:** the "Aapne poocha" kicker in ink3, the quoted question in Fraunces italic 30 sp,
      then three red dots (alpha 1, .6, .3) and "Ek second, dekhta hoon".
    - **`OpenBlock`:**
      - "Dukaan khuli hai" kicker in Ok
      - "Sab theek hai. Munshi sun raha hai." in Fraunces 30 sp
      - the tally row: "₹{ledger.total}" / "is hafte"; "{open count}" / "khule QR";
        "₹{khata.sum}" / "udhaar baaki" (clickable, calls `a.udhaar`)
  - **`Dock`:**

```kotlin
when {
    !s.live -> { DockLine("Jud raha hai…"); /* then the state's buttons, disabled */ }
}
when {
    s.mode == Mode.Listening -> { Waveform(); Text("Sun raha hoon…", Label/Button ivory) }
    s.ask != null -> { StatusRow("Munshi pooch raha hai"); Row { MunshiButton("Haan", a.yes, Modifier.weight(1f), icon = MunshiIcons.Check, enabled = s.live); MunshiButton("Nahi", a.no, Modifier.width(130.dp), ButtonStyle.Secondary, enabled = s.live) }; Tools(a) }
    s.mode == Mode.Working -> { Row { Icon(Spark, RedOnInk); Text("Soch raha hoon… phone par, muft") }; ThinLine(); Tools(a, dim = true) }
    !s.shopOpen -> { MunshiButton("Dukaan kholo", a.openShop, Modifier.fillMaxWidth(), icon = MunshiIcons.Store, enabled = s.live); Tools(a) }
    else -> { Row(SpaceBetween) { MunshiButton("Shelf", a.camera, style = Tool, icon = Camera); Column { MicButton(false, a.mic); Text("Boliye") }; MunshiButton("Band karo", a.closeShop, style = Tool, icon = Close) } }
}
```

    - `Tools(a)` is a centred row of `MunshiButton("Boliye", a.mic, style = Tool, icon = Mic)` and
      `MunshiButton("Shelf photo", a.camera, style = Tool, icon = Camera)`
    - `mind` text (download progress) shows as a small ivory line at the top of the dock when not null,
      as `DockStatus` does today
    - **`Waveform`:** 20 bars, 4 dp wide, in `RedOnInk`, heights animated with
      `rememberInfiniteTransition`; static in inspection mode

- [ ] **Step 3: Wire `MainScreen`:**
  - add `var sheet by remember { mutableStateOf<String?>(null) }` with values "hisaab", "udhaar" and
    "settings"
  - `MainActions(…, week = { sheet = "hisaab" }, settings = { sheet = "settings" }, udhaar = { sheet = "udhaar" }, closeShop = conductor::closeShop, relink = conductor::relink)`
  - `today` is `LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("hi", "IN")))`,
    mapped through a Roman-Hindi table for weekday and month (`Somvaar, Mangalvaar, Budhvaar, Guruvaar,
    Shukravaar, Shanivaar, Ravivaar`; `Janvari … Disambar`); add `fun romanDate(d: LocalDate): String` in
    `ui/theme/Type.kt`
  - the sheets come in Task 6; until then, show `HisaabSheet` for "hisaab" and ignore the others

- [ ] **Step 4: Record and compare.**
  1. Run `:app:recordPaparazziDebug`.
  2. Compare each `MainContentScreenshotTest_*phone*` image with its Figma frame (C1–C6).
  3. Fix spacing and colour mismatches.
  4. Re-record.
  5. Run all unit tests.
- [ ] **Step 5: Commit** with `feat(android): counter screen in six states, QR stack, dock by state`.

---

### Task 6: Sheets

**Files:**
- Create: `ui/components/UdhaarSheet.kt`, `ui/components/SettingsSheet.kt`
- Modify: `ui/components/HisaabSheet.kt` (use `MunshiSheet`), `ui/screens/MainScreen.kt`
- Test: `ui/SheetsScreenshotTest.kt` (new)

**Interfaces:**
- Produces:
  - `UdhaarSheet(khata: List<FactKhata>, onRemind: (Long) -> Unit, onDismiss)`
  - `SettingsSheet(info: SettingsInfo, actions: SettingsActions, onDismiss)`, where
    - `data class SettingsInfo(val profile: Profile?, val brain: String, val brainOk: Boolean, val server: String)`
    - `data class SettingsActions(val editShop: () -> Unit, val repair: () -> Unit, val redoOnboarding: () -> Unit)`

- [ ] **Step 1: Write the screenshot tests.** Paparazzi cannot render a `ModalBottomSheet` window, so each
  sheet's body is a separate composable:
  - `UdhaarBody`, `HisaabBody` and `SettingsBody`, each a `ColumnScope` extension used inside
    `MunshiSheet`
  - the tests render each body in a paper `Column` with 24 dp padding

  Write `@Test fun udhaar()`, `hisaab()` and `settings()` with the Figma data:
  - khata: Sharma ji 450/12 and Pinky didi 230/9
  - ledger as in the main test, plus a loan of `Loan(50000, 52400, 520, 100)`
  - settings: `SettingsInfo(Profile("Ramesh Kirana Store", "Aliganj, Lucknow", addressAs = "Ramesh ji", phone = "+919876543210"), "Gemma 4 E4B · taiyaar · GPU par", true, "abc-def.trycloudflare.com")`

- [ ] **Step 2: Implement the bodies.**
  - **`UdhaarBody`:**
    - `Kicker("Udhaar")`
    - the title "₹{sum} baaki, {n} log" (Fraunces SemiBold 28)
    - for each khata, a `TaskRow(kicker = "${k.days} din se baaki", who = k.name, amount = k.amount)` whose
      `trailing` is an ink pill with the Bell icon and "Yaad dilao", calling `onRemind(k.khataId)`
    - a footer in ink3: "Munshi khud bhi poochega jab udhaar 7 din se zyada ho."
  - **`HisaabBody`:**
    - `Kicker("Is hafte")` and the title "Munshi ne ₹X dilaye"
    - one row per ledger item: a kicker from `item.kind` (`udhaar_recovered` → "Udhaar wapas", `winback`
      → "Win-back", `group_buy_saving` → "Group order bachat", otherwise "Kamaai"), the label and "+₹amount"
    - the loan panel (ink at 4 %, radius 14): "Diwali stock loan", the principal, and "Kamaai mein nahi gina"
  - **`SettingsBody`:**
    - `Kicker("Dukaan")`, the shop name as title, and "{locality} · {phone}" in ink3
    - six rows (icon, title, subtitle, chevron); every row is clickable except the read-only "Munshi ka
      dimaag" and "Awaaz":
      - Store "Dukaan ki jaankari" → `editShop`
      - Spark "Munshi ka dimaag" (subtitle in Ok when `brainOk`)
      - Wifi "Server se juda hai"
      - Mic "Awaaz", with the subtitle "Android Hindi voice"
      - Qr "Dobara jodiye" → `repair`
      - Speaker "Onboarding phir se" → `redoOnboarding`

- [ ] **Step 3: Wire them in `MainScreen`:**
  - "udhaar": `UdhaarSheet(state.khata, { id -> sheet = null; conductor.remind(id) }) { sheet = null }`
  - "settings":
    - the brain text comes from `app.brain.state`: `Ready` → "Gemma · taiyaar"; `Downloading` → the
      `mindNote` text; `Missing` → "Wi-Fi par download hoga"; `Unsupported` → "Is phone par AI nahi, templates chalte hain"
    - `editShop` and `redoOnboarding` call into the onboarding store (Task 7); `repair` calls `onRepair`
  - **`MainScreen` signature:** becomes
    `MainScreen(conductor, settings: SettingsInfo, onRepair, onEditShop, onRedoOnboarding)`. `MainActivity`
    builds `SettingsInfo` from `graph.config.url`, the profile in the conductor state, and
    `app.brain.state`.

- [ ] **Step 4: Record, compare with the Figma Sheets page, and run the tests.**
- [ ] **Step 5: Commit** with `feat(android): udhaar, hisaab and settings sheets`.

---

### Task 7: Onboarding state

**Files:**
- Create: `onboarding/Onboarding.kt`, `onboarding/ProgressStore.kt`
- Test: `onboarding/OnboardingTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class Step { Welcome, Connect, Number, Otp, Shop, Permissions, Brain, Voice }
data class Progress(val step: Step = Step.Welcome, val done: Boolean = false, val editOnly: Boolean = false)
interface ProgressStore { val progress: Flow<Progress>; suspend fun save(p: Progress) }
class DataStoreProgress(context: Context) : ProgressStore      // preferencesDataStore("onboarding")
data class OnboardingUi(val step: Step, val phone: String = "", val otp: String = "", val profile: Profile? = null,
                        val busy: Boolean = false, val error: String? = null, val heard: String? = null, val voiceTried: Boolean = false)
class Onboarding(store: ProgressStore, scope: CoroutineScope, otpDelayMs: Long = 1_200) {
    val ui: StateFlow<OnboardingUi?>       // null until the store is read
    val done: StateFlow<Boolean?>
    fun next(); fun back(); fun setPhone(s: String); fun setProfile(p: Profile)
    fun lookup(api: MunshiApi); fun save(api: MunshiApi); fun voice(check: suspend () -> String?)
    fun finish(); fun redo(); fun editShop(); fun paired(); fun reconnect()
}
```

- [ ] **Step 1: Write the failing tests:**

```kotlin
class MemoryProgress(start: Progress = Progress()) : ProgressStore {
    val saved = MutableStateFlow(start)
    override val progress: Flow<Progress> = saved
    override suspend fun save(p: Progress) { saved.value = p }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {
    private val api = FakeApi()

    @Test fun `steps go in order and are saved, so a restart resumes`() = runTest {
        val store = MemoryProgress(); val o = Onboarding(store, backgroundScope); runCurrent()
        assertEquals(Step.Welcome, o.ui.value!!.step)
        o.next(); runCurrent()
        assertEquals(Step.Connect, store.saved.value.step)
        val again = Onboarding(store, backgroundScope); runCurrent()
        assertEquals(Step.Connect, again.ui.value!!.step)
    }

    @Test fun `pairing moves Connect on to the number`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Connect)), backgroundScope); runCurrent()
        o.paired(); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
    }

    @Test fun `a bad number is said plainly; a good one finds the shop and the OTP fills itself`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Number)), backgroundScope); runCurrent()
        o.setPhone("123"); o.lookup(api); runCurrent()
        assertEquals("10 ank ka number daaliye.", o.ui.value!!.error)
        o.setPhone("98765 43210"); o.lookup(api); runCurrent()
        assertEquals(Step.Otp, o.ui.value!!.step)
        assertEquals("Ramesh Kirana", o.ui.value!!.profile!!.shopName)
        assertEquals("", o.ui.value!!.otp)
        advanceTimeBy(1_300); runCurrent()
        assertEquals(6, o.ui.value!!.otp.length)
    }

    @Test fun `a server error keeps the step and says so`() = runTest {
        api.lookupFails = true
        val o = Onboarding(MemoryProgress(Progress(Step.Number)), backgroundScope); runCurrent()
        o.setPhone("9876543210"); o.lookup(api); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
        assertEquals("Server se baat nahi hui. Dobara koshish kijiye.", o.ui.value!!.error)
    }

    @Test fun `confirming the shop saves it and moves on`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Shop)), backgroundScope); runCurrent()
        o.setProfile(api.profile.copy(addressAs = "Suresh bhai")); o.save(api); runCurrent()
        assertTrue("save:Suresh bhai" in api.calls)
        assertEquals(Step.Permissions, o.ui.value!!.step)
    }

    @Test fun `the voice check records what was heard; finishing marks onboarding done`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.voice { "haan" }; runCurrent()
        assertEquals("haan", o.ui.value!!.heard)
        o.finish(); runCurrent()
        assertTrue(store.saved.value.done)
        assertEquals(true, o.done.value)
    }

    @Test fun `editing the shop from settings opens only that step`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.editShop(); runCurrent()
        assertEquals(Progress(Step.Shop, done = false, editOnly = true), store.saved.value)
        o.setProfile(api.profile); o.save(api); runCurrent()
        assertTrue(store.saved.value.done)
    }

    @Test fun `a cleared pairing reopens Connect, and pairing again carries on to the number`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.reconnect(); runCurrent()
        assertEquals(Progress(Step.Connect), store.saved.value)
        o.paired(); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
    }

    @Test fun `onboarding again starts at Welcome`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.redo(); runCurrent()
        assertEquals(Progress(Step.Welcome), store.saved.value)
    }
}
```

- [ ] **Step 2: Run them.** Expected: compile failure.

- [ ] **Step 3: Implement `ProgressStore.kt`:**

```kotlin
private val Context.onboardingStore by preferencesDataStore("onboarding")
private val STEP = stringPreferencesKey("step"); private val DONE = booleanPreferencesKey("done"); private val EDIT = booleanPreferencesKey("editOnly")

class DataStoreProgress(private val context: Context) : ProgressStore {
    override val progress: Flow<Progress> = context.onboardingStore.data.map { p ->
        Progress(p[STEP]?.let { s -> Step.entries.firstOrNull { it.name == s } } ?: Step.Welcome, p[DONE] ?: false, p[EDIT] ?: false)
    }
    override suspend fun save(p: Progress) { context.onboardingStore.edit { it[STEP] = p.step.name; it[DONE] = p.done; it[EDIT] = p.editOnly } }
}
```

- [ ] **Step 4: Implement `Onboarding.kt`:**

```kotlin
// The merchant's first run, one step per screen; the step is saved so a closed app resumes there.
class Onboarding(private val store: ProgressStore, private val scope: CoroutineScope, private val otpDelayMs: Long = 1_200) {
    private val _ui = MutableStateFlow<OnboardingUi?>(null)
    val ui: StateFlow<OnboardingUi?> = _ui
    private val _done = MutableStateFlow<Boolean?>(null)
    val done: StateFlow<Boolean?> = _done
    private var progress = Progress()

    init {
        scope.launch {
            progress = store.progress.first()
            _done.value = progress.done
            _ui.value = OnboardingUi(progress.step)
        }
    }

    private fun go(step: Step, done: Boolean = false, editOnly: Boolean = progress.editOnly) {
        progress = Progress(step, done, editOnly)
        _ui.update { (it ?: OnboardingUi(step)).copy(step = step, error = null, busy = false) }
        _done.value = done
        scope.launch { store.save(progress) }
    }

    fun next() { val s = _ui.value?.step ?: return; Step.entries.getOrNull(s.ordinal + 1)?.let { go(it) } ?: finish() }
    fun back() { val s = _ui.value?.step ?: return; Step.entries.getOrNull(s.ordinal - 1)?.let { go(it) } }
    fun paired() { if (_ui.value?.step == Step.Connect) go(Step.Number) }
    fun setPhone(s: String) = _ui.update { it?.copy(phone = s.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(16), error = null) }
    fun setProfile(p: Profile) = _ui.update { it?.copy(profile = p, error = null) }

    fun lookup(api: MunshiApi) {
        val phone = _ui.value?.phone.orEmpty()
        val digits = phone.filter(Char::isDigit).removePrefix("91").takeLast(11)
        if (digits.length != 10) { _ui.update { it?.copy(error = "10 ank ka number daaliye.") }; return }
        busy { api.lookup(phone).also { p -> _ui.update { it?.copy(profile = p) }; go(Step.Otp); fillOtp() } }
    }

    private fun fillOtp() = scope.launch { delay(otpDelayMs); _ui.update { it?.copy(otp = "482917") } }   // demo: as if read from the SMS

    fun save(api: MunshiApi) {
        val p = _ui.value?.profile ?: return
        if (p.shopName.isBlank() || p.addressAs.isBlank()) { _ui.update { it?.copy(error = "Dukaan ka naam aur aapko kya bulaayein, dono chahiye.") }; return }
        busy { val saved = api.saveMerchant(p); _ui.update { it?.copy(profile = saved) }; if (progress.editOnly) go(Step.Voice, done = true, editOnly = false) else go(Step.Permissions) }
    }

    fun voice(check: suspend () -> String?) {
        _ui.update { it?.copy(busy = true, heard = null) }
        scope.launch { val h = check(); _ui.update { it?.copy(busy = false, heard = h, voiceTried = true) } }
    }

    fun finish() = go(Step.Voice, done = true, editOnly = false)
    fun reconnect() = go(Step.Connect, done = false, editOnly = false)   // the pairing was cleared: scan again, then carry on
    fun redo() = go(Step.Welcome, done = false, editOnly = false)
    fun editShop() = go(Step.Shop, done = false, editOnly = true)

    private fun busy(block: suspend () -> Unit) {
        _ui.update { it?.copy(busy = true, error = null) }
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it?.copy(busy = false, error = "Server se baat nahi hui. Dobara koshish kijiye.") } }
        }
    }
}
```

  The `editShop()` test expects the saved progress after `editShop()` to be
  `Progress(Step.Shop, done = false, editOnly = true)`. That matches `go(Step.Shop, done = false, editOnly = true)`.

- [ ] **Step 5: Run the tests.** Expected: PASS.
- [ ] **Step 6: Commit** with `feat(android): onboarding steps, saved progress, lookup/save/voice check`.

---

### Task 8: Onboarding screens and routing

**Files:**
- Create: `ui/onboarding/OnboardingContent.kt` (all eight step bodies plus the shared frame),
  `ui/onboarding/OnboardingFlow.kt` (the stateful wrapper)
- Modify: `MunshiApp.kt` (an `onboarding` singleton; `Graph` exposes `api`), `MainActivity.kt` (routing,
  permissions), `ui/screens/PairScreen.kt` (deleted; its scanner moves into Connect)
- Test: `ui/OnboardingScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 7 `Onboarding`/`OnboardingUi`/`Step`; Task 3 components; `BrainState`;
  `Conductor.voiceCheck`.
- Produces:
  - `OnboardingContent(ui: OnboardingUi, env: OnboardingEnv, a: OnboardingActions)`, where
    - `data class OnboardingEnv(val modelName: String, val brain: BrainState, val micGranted: Boolean, val notifyGranted: Boolean, val serverError: String? = null)`
    - `data class OnboardingActions(val next: () -> Unit, val back: () -> Unit, val scan: () -> Unit, val typeUrl: (String) -> Unit, val phone: (String) -> Unit, val lookup: () -> Unit, val profile: (Profile) -> Unit, val save: () -> Unit, val askMic: () -> Unit, val askNotify: () -> Unit, val voice: () -> Unit, val finish: () -> Unit)`

- [ ] **Step 1: Write the screenshot tests,** one per Figma frame:

```kotlin
class OnboardingScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)
    private val env = OnboardingEnv("Gemma 4 · E4B", BrainState.Downloading(1_500_000_000, 3_659_530_240), micGranted = true, notifyGranted = false)
    private val a = OnboardingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val shop = Profile("Ramesh Kirana Store", "Aliganj, Lucknow", "Kirana · rozmarra ka saamaan", "Ramesh Gupta", "Ramesh ji", "+919876543210")
    private fun shot(ui: OnboardingUi, e: OnboardingEnv = env) = paparazzi.snapshot { OnboardingContent(ui, e, a) }

    @Test fun welcome() = shot(OnboardingUi(Step.Welcome))
    @Test fun connect() = shot(OnboardingUi(Step.Connect))
    @Test fun number() = shot(OnboardingUi(Step.Number, phone = "+91 98765 43210"))
    @Test fun otp() = shot(OnboardingUi(Step.Otp, phone = "+91 98765 43210", otp = "482917", profile = shop))
    @Test fun shop() = shot(OnboardingUi(Step.Shop, profile = shop))
    @Test fun permissions() = shot(OnboardingUi(Step.Permissions))
    @Test fun brain() = shot(OnboardingUi(Step.Brain))
    @Test fun voiceHeard() = shot(OnboardingUi(Step.Voice, profile = shop, heard = "haan", voiceTried = true))
    @Test fun voiceNothing() = shot(OnboardingUi(Step.Voice, profile = shop, voiceTried = true))
    @Test fun numberError() = shot(OnboardingUi(Step.Number, phone = "123", error = "10 ank ka number daaliye."))
}
```

- [ ] **Step 2: Implement `OnboardingContent.kt`.**
  - **The shared frame** (`StepFrame(step, kicker, title, body, dock: @Composable ColumnScope.() -> Unit, content)`):
    - paper background with the margin line (`LedgerPage`), padded to `safeDrawing`
    - from Connect on, a progress row: 6 bars, the current one 28 dp and the rest 14 dp, red up to the
      current step, rule after it, and "n / 6"
    - then the kicker, the title in Display/Greeting and the body in Body/Regular ink2, as a column at
      40 dp start and 24 dp end
    - the dock is `InkDock` at the bottom, and `ui.error` shows in red above the dock's button
    - on system back, `a.back()` is called (`BackHandler`) except on Welcome
  - **The steps:**

| Step | Content | Dock |
|---|---|---|
| Welcome | Wordmark "Munshi" (Fraunces 58) with a red dot; italic tagline "Aapki dukaan ka munshi. Bas boliye, kaam ho jaayega."; card with the Speaker kicker and the Sharma sample line; benefits with a redSoft badge and icon (Bell, Rupee, Mic) | `MunshiButton("Shuru karein", next)` and "Paytm for Business ke saath" |
| Connect | Dashed card (`drawBehind` with a `PathEffect.dashPathEffect`) with the Qr icon at 64 dp and "Laptop ki screen par QR"; "Ya server ka pata likhiye ›" reveals a `FieldRow("Server ka pata")` that calls `typeUrl` on Done | `MunshiButton("QR scan kijiye", scan, icon = Camera)` |
| Number | `FieldRow("Mobile number", ui.phone, phone, big = true, keyboard = Phone)`; warn pill "Demo: koi bhi number chalega" | `MunshiButton("OTP bhejo", lookup, enabled = !busy)` |
| Otp | Six 48 × 58 boxes showing `ui.otp` digits; ok pill with a check and "Demo: OTP apne aap bhara gaya" once filled; "Dobara bhejein" in ink3 | `MunshiButton("Aage", next, icon = Check, enabled = otp.length == 6)` |
| Shop | Ok line "Paytm se mili jaankari · badalne ke liye tap kijiye"; card of five `FieldRow`s bound to `profile`, the last one `accent = true` | `MunshiButton("Haan, sahi hai", save, icon = Check, enabled = !busy)` |
| Permissions | Two cards, each with a redSoft badge (Mic, Bell), title, reason and a pill: "Dein" (ink, clickable) or "Mil gayi" (ok tint with a check) | `MunshiButton("Aage", next)` |
| Brain | Card with a Spark badge on a warn tint, `env.modelName`, "Aapke {ram} GB phone ke liye", progress track (red fill = done / total), "x.x GB / y.y GB" and "Wi-Fi par"; when Ready, "Taiyaar" in ok and a full bar; Unsupported says "Is phone par AI nahi chalega; Munshi templates se kaam karega"; note panel "Tab tak Munshi bina AI ke kaam karega…" | `MunshiButton("Aage badhein", next)` and "Download peeche chalta rahega" |
| Voice | Card with the Speaker kicker and "“Namaste {addressAs}, main Munshi hoon. Sun pa rahe hain? Haan boliye.”"; centred `MicButton(listening = busy, voice)` at 112 dp; under it "Sun raha hoon…" when busy, the ok pill "Aapne kaha: “{heard}”" when heard, or "Kuch sunaai nahi diya" with a *Dobara* text button when `voiceTried && heard == null` | `MunshiButton("Taiyaar! Dukaan kholiye", finish, icon = Check)` and "Baad mein bhi kar sakte hain" (clickable, also `finish`) |

- [ ] **Step 3: Implement `OnboardingFlow.kt`** (stateful):

```kotlin
@Composable
fun OnboardingFlow(app: MunshiApp, onboarding: Onboarding) {
    val ui by onboarding.ui.collectAsStateWithLifecycle()
    val graph by app.graph.collectAsStateWithLifecycle()
    val brain by app.brainState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mic by remember { mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO)) }
    var notify by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }
    LaunchedEffect(graph, ui?.step) { if (graph != null && ui?.step == Step.Connect) onboarding.paired() }
    // The Shop step needs the looked-up profile; after a restart, read it from the server.
    LaunchedEffect(graph, ui?.step) { val g = graph; if (g != null && ui?.step == Step.Shop && ui?.profile == null) runCatching { g.api.merchant() }.getOrNull()?.let(onboarding::setProfile) }
    val u = ui ?: return
    OnboardingContent(u, OnboardingEnv(app.modelName, brain, mic, notify), OnboardingActions(
        next = onboarding::next, back = onboarding::back,
        scan = { scanSetupQr(context) { c -> scope.launch { app.configs.save(c) } } },
        typeUrl = { url -> parseConfig("""{"url":"${url.trim()}","key":""}""")?.let { c -> scope.launch { app.configs.save(c) } } },
        phone = onboarding::setPhone, lookup = { graph?.let { onboarding.lookup(it.api) } },
        profile = onboarding::setProfile, save = { graph?.let { onboarding.save(it.api) } },
        askMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
        askNotify = { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) },
        voice = { graph?.let { g -> onboarding.voice { g.conductor.voiceCheck(u.profile?.addressAs ?: "Ramesh ji") } } },
        finish = onboarding::finish,
    ))
}
```

  `scanSetupQr(context, onConfig)` moves the `GmsBarcodeScanning` code out of `PairScreen`, unchanged.
  `granted(context, p)` is `ContextCompat.checkSelfPermission(context, p) == PERMISSION_GRANTED`.

- [ ] **Step 4: Update `MunshiApp` and `MainActivity`:**
  - **`MunshiApp`:**
    - `lateinit var onboarding: Onboarding`, created in `onCreate` with `Onboarding(DataStoreProgress(this), scope)`
    - `val brainState get() = mind.state`
    - `val modelName: String`, built from `Models.forRam(totalRam)?.name`: "gemma-4-E4B…" → "Gemma 4 · E4B",
      "…E2B…" → "Gemma 4 · E2B", otherwise "Is phone par nahi"
    - `Graph` gains `val api = HttpMunshiApi(config.url, config.key, http)`, which the conductor reuses
  - **`MainActivity.setContent`:**

```kotlin
val done by app.onboarding.done.collectAsStateWithLifecycle()
val g = graph
when {
    done == null -> Box(Modifier.fillMaxSize().background(Tokens.Paper))           // reading the store
    done == false || g == null -> OnboardingFlow(app, app.onboarding)
    else -> MainScreen(g.conductor, settingsInfo(app, g), onRepair = { scope.launch { app.configs.clear(); app.onboarding.redo() } },
        onEditShop = app.onboarding::editShop, onRedoOnboarding = app.onboarding::redo)
}
```

  - **A cleared pairing:** when `done == true` but `g == null` (the pairing was cleared), the flow should
    open at Connect. In `OnboardingFlow`, add
    `LaunchedEffect(graph) { if (graph == null && (ui?.step?.ordinal ?: 0) > Step.Connect.ordinal) onboarding.reconnect() }`.
    `reconnect()` comes from Task 7.
  - **Permissions:** remove the `permissions.launch(...)` in `onCreate`. Onboarding asks; Boliye with no
    mic grant is handled by `AndroidListener.available` (false), so also show the toast "Mic ki ijaazat
    Settings mein dijiye" from `MainScreen` when `mic` is tapped without the grant.
  - Delete `PairScreen.kt`.

- [ ] **Step 5: Record, compare every screen with the Figma Onboarding page, and fix any mismatch.**
- [ ] **Step 6: Run** all unit tests and `npm test`. Expected: PASS.
- [ ] **Step 7: Commit** with `feat(android): onboarding flow (8 steps), routing, permissions asked in onboarding`.

---

### Task 9: On the phone

**Files:**
- Create: `docs/superpowers/notes/2026-10-onboarding-and-ui.md`

- [ ] **Step 1: Set up.** Start the server (`npm run server`), run `adb reverse tcp:3000 tcp:3000`, then
  `adb shell pm clear com.opuscube.munshi` and install with `:app:installDebug`. The model file stays in
  `files/models`, so push it again with `android/scripts/push-model.ps1` if `pm clear` removed it.
- [ ] **Step 2: Go through onboarding by hand on the Galaxy A34:**
  - type the server address (the scanner needs the laptop page)
  - number 9876543210, then OTP
  - change "Munshi aapko kya bulaye" to "Suresh bhai" and save
  - grant both permissions
  - Brain shows Gemma 4 · E4B (taiyaar)
  - voice check: say "haan"
  - take a screenshot of each step with `adb exec-out screencap -p`
- [ ] **Step 3: Check the names.**
  - Morning brief: "Shubh prabhat, Suresh bhai".
  - *Dukaan kholo*: the spoken greeting says "Suprabhat Suresh bhai".
  - Haan on Sharma ji: the WhatsApp text (or its dry-run log on the server) says "{saved shop name} se
    yaad dilaana tha".
- [ ] **Step 4: Check the counter screen.**
  - Run the end-to-end script (`scratchpad/e2e.sh`, or its copy under `android/scripts/e2e.sh` if added),
    and keep the dock states and QR stack screenshots.
  - Confirm both Sharma and Pinky QRs stack.
  - Advance the clock 61 minutes (`POST /api/control/advance {"minutes":61}`) and check that a card
    turns Expired and "Naya link bhejein" asks again.
- [ ] **Step 5: Check the sheets and settings.**
  - Udhaar sheet: *Yaad dilao* asks for Pinky.
  - Settings: "Onboarding phir se" returns to Welcome; "Dobara jodiye" returns to Connect.
- [ ] **Step 6: Write the notes,** listing what was checked, any differences from Figma, and what was
  fixed.
- [ ] **Step 7: Commit** with `docs: onboarding and redesign checked on the Galaxy A34`.
