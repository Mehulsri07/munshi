# Munshi Android app and UI redesign

**Team:** OpusCube · **Date:** 2026-10-25 · **Target:** demo-ready in 2–3 weeks (finale build)
**Repo:** github.com/CodexManvik/munshi (server + web today; this adds `android/` and restyles `web/`)
**Approved mockup:** `docs/design/soundbox-app-mockup.html` (visual reference; open in a browser)

## 1. Goal

Replace the web `/soundbox` face with a native Android app that is the Soundbox on the counter, and give
every surface (app and remaining web pages) one premium visual language taken from the Round 1 deck.

Success: on stage, an Android phone or tablet speaks first in Hindi, takes "haan" by voice or tap, shows the
pay QR, stamps "Chuka diya" when the judge pays, and never blocks if the network, Claude, WhatsApp, the mic or
the camera fails.

Out of scope: wake word, merchant-phone/owner mode, iOS, offline queueing of actions, Play Store release,
changes to detector or agent logic.

## 2. Decisions already made

| Decision | Choice |
|---|---|
| App role | The Soundbox face on the counter (replaces `/soundbox`) |
| Devices | Phone portrait primary; tablet (incl. landscape) supported |
| Approach | Native Kotlin + Jetpack Compose, custom theme |
| Server | Same Node server and API; only additive changes (§7) |
| Look | "Ledger page + ink dock" (§3) |

## 3. Visual language: Ledger page + ink dock

**Tokens** (one source, `design/tokens.json`, generating CSS variables and `Tokens.kt`):

| Token | Value | Use |
|---|---|---|
| paper | `#F4EEE2` | page background |
| rule | `#E7E0D2` | ledger lines every 32 dp; hairlines |
| margin | `#B3261E` at 45% | 1 dp vertical margin line, 20 dp from the left edge |
| ink | `#1A1815` | text; dock background |
| ink2 / ink3 | `#5E5A52` / `#948E83` | secondary / tertiary text |
| red | `#B3261E` (dock: `#C42F25`→`#A7231B` vertical gradient) | accent; Haan; stamp |
| red-on-ink | `#D2452F` | waveform, live dots on the dock |
| ivory | `#F4EEE2` | text on the dock |

**Type:** Fraunces (headlines, names, rupee figures at optical size 144, spoken lines in italic) and Inter
(labels, body, buttons), both bundled as font resources. Rupee figure: semibold, tracking −3.5%, small raised
₹ at 48% size. Kicker labels: 9–11 sp, bold, uppercase, tracking 16%, red.

**Page:** paper with faint ruled lines and the red margin line (as in the deck). Top bar: "Munshi" wordmark
with a small red dot, and the week chip ("Is hafte ₹742") as an outlined pill.

**Ink dock:** a floating rounded panel (radius 26 dp, 10 dp inset from the edges, soft shadow, 1 dp top
highlight). Holds the status line (listening waveform or "Kar raha hoon…"), Haan (red gradient pill, ≈1.6×
the width of Nahi, 54 dp tall), Nahi (ivory outline), and the Boliye / Shelf photo tools. On a tablet in
landscape the dock becomes a right-hand panel.

**Motion:** speaking = three red bars pulsing beside the kicker; listening = red waveform in the dock;
payment = round red "Chuka diya · date" stamp drops in (scale 1.6→0.96→1, rotated −12°) while the week chip
flashes red and counts up. Respect the system "remove animations" setting.

## 4. Screens and states

**Pair (first launch).** "Jodiye": scan the QR on `/control` that encodes `{url, key}`; stored in DataStore.
Long-press the wordmark to re-pair.

**Main screen states:**

| State | Page | Dock |
|---|---|---|
| Brief | "Shubh prabhat, Ramesh ji", today's top 3 tasks with ₹, total | Dukaan kholo |
| Speaking | kicker with speaking bars, who, ₹ figure, italic line, tags | Haan / Nahi enabled (early answers accepted) |
| Listening | as above + "Aapne kaha: …" (partial results) | waveform, Haan / Nahi |
| Working | line at 60% opacity | "Kar raha hoon…" + thin progress line |
| Pay QR | ledger-style QR card: "Sharma ji, scan karke chukayein · ₹450", stays until that ref is paid | next question if any |
| Payment lands | stamp, chime, week chip tick, spoken announcement | next task |
| Announcement | slim ink banner slides in from the top for 6.5 s | — |
| Shelf | full-screen CameraX → "Dekh raha hoon…" → what Munshi saw | restock / group-order proposal |
| Reconnecting | unchanged | "Jud raha hai…" dot; Haan / Nahi disabled |

**Hisaab sheet.** Tapping the week chip opens a bottom sheet: total, each ledger line with its action, and
the loan apart ("kamaai mein nahi gina").

## 5. Voice and audio

- **Speaker:** Android `TextToSpeech`, one FIFO queue; announcements wait behind questions. If a line has
  `spoken` (Devanagari) use the hi-IN voice on it; otherwise the en-IN voice on the Roman line. No Hindi voice
  → en-IN; no voice at all → captions only.
- **Speaks first:** `nudge` and `brief` events, and the optional idle tick (setting, off by default on stage),
  start a session without any tap.
- **Listener:** Android `SpeechRecognizer`, hi-IN, prefer offline, partial results. After each question,
  auto-listen for 6 s; mic button listens on demand.
- **IntentMatcher (fast path):** yes = haan, ha, haa, han, हाँ, हां, bhej do, bhejo, theek hai, kar do, ok;
  no = nahi, nahin, नहीं, mat, rehne do, abhi nahi. Exact/whole-phrase match after normalising case and
  punctuation. Anything else → `/api/converse`.
- **Barge-in:** a tap or a recognised haan/nahi stops speech immediately and acts.
- **Sounds/haptics:** two-note chime (587.33 Hz then 880 Hz) on payment; soft tick on listen start; light
  haptic on Haan.
- **Counter mode:** screen kept on; a foreground service keeps the event stream and announcements alive in
  the background, with a persistent notification "Munshi dukaan par hai".

## 6. Android architecture

`android/` in the repo; one Gradle module, one activity, manual wiring (no DI framework). minSdk 26.

| Package | Responsibility | Key interface |
|---|---|---|
| `api` | Typed client mirroring `web/api.ts` (OkHttp + kotlinx.serialization); sends `x-demo-key` | `MunshiApi` |
| `events` | SSE via okhttp-sse, reconnect with backoff 1→15 s | `fun events(): Flow<SoundboxEvent>`, `val live: StateFlow<Boolean>` |
| `voice` | `Speaker`, `Listener`, `IntentMatcher` | `suspend fun say(line: Line)`, `fun listen(windowMs: Long): Flow<Heard>`, `fun match(text: String): Answer?` |
| `conductor` | Port of `runSession` / `resolveProposal` / `onShelf` as a coroutine state machine; owns `UiState` | `val state: StateFlow<UiState>`, `fun answer(a: Answer)`, `fun openShop()`, `fun shelf(jpeg: ByteArray)` |
| `ui` | Theme from tokens; components: `LedgerPage`, `InkDock`, `RupeeFigure`, `ChukaDiyaStamp`, `WeekChip`, `QrCard`, `Banner`, `HisaabSheet`; screens: `PairScreen`, `MainScreen`, `CameraScreen`; adaptive via window size class | renders `UiState` |
| `camera` | CameraX capture → longest side 1280 px, JPEG q82 → base64 → `/api/shelf`; gallery fallback | `suspend fun capture(): ByteArray` |
| `pairing` | ML Kit barcode scan; DataStore for `{url, key}` | `val config: Flow<Config?>` |
| `service` | Foreground service hosting `events` + `Speaker` for announcements | — |

Behaviour preserved from `web/Soundbox.tsx`: one proposal at a time; answers accepted while speaking; free
speech while a proposal is pending goes to converse, and if converse returns a different `opportunityId` with no
pay link, that proposal is resolved next; a shelf result skips the pending question, says what was seen, and
the session picks up the restock offer; a pay QR persists until its `paid` event; `reset` returns to Brief.

Libraries: Compose BOM, OkHttp + okhttp-sse, kotlinx-serialization, CameraX, ML Kit barcode scanning,
ZXing core (QR drawing), DataStore.

## 7. Server changes (additive; web keeps working)

1. `GET /api/brief` → `{ items: [{ id, type, title, amount }], total }`: the top three by ₹ for the brief
   scope, without marking them proposed.
2. Proposal and result lines gain optional `display: { who, amount, rest }` and `spoken` (Devanagari).
   Claude phrasing returns both when available; template lines carry hand-written Devanagari.
3. `paid` events gain `{ name, amount }`.
4. `/control` shows a pairing QR encoding `{"url": PUBLIC_URL, "key": DEMO_KEY}`.

## 8. Web re-skin

Same tokens and fonts, self-hosted. Order: `/pay/:ref` (judges' phones; must load fast on mobile data),
`/ledger`, `/paytm`, `/control` (plus pairing QR), then `/soundbox` restyled as the stage fallback. The pay page
keeps saying the payment is simulated.

## 9. Failure handling

| Failure | Behaviour |
|---|---|
| Server unreachable / stream drops | Backoff reconnect; "Jud raha hai…"; Haan/Nahi disabled; on reconnect re-fetch pending state |
| `reset` event | Back to Brief, nothing stale pending |
| 409 `not_proposed` on confirm | Say "Yeh kaam ab baaki nahi", fetch next |
| Claude down | Server templates; no `spoken` → en-IN voice |
| No TTS / no Hindi voice | en-IN, else captions only |
| Mic denied / no recogniser | Hide auto-listen; buttons work |
| Camera denied | Gallery picker; `/control` shelf beat as last resort |
| WhatsApp down | QR drawn on-device from `payUrl` |
| Duplicate `paid` | Stamp and chime once per ref |

## 10. Testing

- Conductor unit tests with fakes: brief of 3; early answer during speech; shelf interrupt; converse
  redirect; QR persists until paid; 409; reconnect mid-question; reset.
- IntentMatcher table tests (Roman and Devanagari).
- API contract tests: Kotlin models parse JSON fixtures captured from the real server.
- Paparazzi screenshot tests for every state, phone and tablet.
- Server `node:test` for §7.
- Emulator run via adb with screenshots of each state; full rehearsal with a real phone paying on `/pay`.
- CI: build the debug APK and run unit + screenshot tests on every push.

## 11. Build order

Three implementation plans, in this order: **(a)** tokens + server additions (§3 tokens file, §7),
**(b)** the Android app (§4–6, §9–10), **(c)** the web re-skin (§8). (b) and (c) both depend on (a) and are
independent of each other.

| Days | Work | Done when |
|---|---|---|
| 1–2 | Tokens file; server additions §7 | node tests pass; `/control` shows pairing QR |
| 3–6 | `api`, `events`, `conductor`, `pairing` on plain screens | conductor tests pass; app pairs and runs a brief on the emulator |
| 6–8 | `voice` | speaks first, auto-listens, haan by voice works on a device |
| 8–12 | Full UI: page, dock, figure, stamp, motion, tablet, camera, Hisaab | screenshot tests for all states; matches the mockup |
| 12–15 | Web re-skin | pay, ledger, impact, control, fallback soundbox restyled |
| 15+ | Rehearsal and stage hardening | full finale script runs twice clean on the stage device |
