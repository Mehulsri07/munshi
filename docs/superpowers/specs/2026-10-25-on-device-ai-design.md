# Munshi on-device AI, thin server and APK release

**Team:** OpusCube · **Date:** 2026-10-25 · **Builds on:** `2026-10-25-android-app-and-ui-design.md` (PR #1)
**Goal in one line:** the merchant's phone does all the AI and the voice for ₹0 a request; a thin laptop
server, reached through a free Cloudflare tunnel, keeps the ledger, the money links and the secrets.

## 1. Goal

Remove every dependency on the Claude API and show a low-cost stack that runs on free tiers for the demo.
Munshi stays online; the aim is lower cost, not offline use.

Success:
- On an 8 GB Android phone, Munshi rewords lines, answers spoken questions and reads a shelf photo with
  Gemma 4 E4B on the device.
- It speaks Hindi with the phone's own Android TTS voice (chosen in the §6 voice test).
- A customer pays through a 1-hour link that reaches the laptop through a Cloudflare tunnel.
- Nothing calls a paid AI API.
- A debug-signed APK is on the GitHub Releases page.

Out of scope:
- Sending raw audio to Gemma (speech goes through `SpeechRecognizer` text, as today)
- A fully offline mode, and offline queueing of actions
- Real UPI money movement (the pay page stays a labelled simulation)
- Play Store signing, iOS, NPU backends, fine-tuning
- Writing our own Hindi phonemiser for Kokoro

## 2. Decisions

| Decision | Choice |
|---|---|
| AI | Gemma 4 E4B (`.litertlm`) on the phone through LiteRT-LM; GPU backend, CPU fallback |
| Claude | Removed: `@anthropic-ai/sdk`, `ANTHROPIC_API_KEY` and `MUNSHI_MODEL` go; the server keeps rules and templates only |
| Voice | Android TTS hi-IN, chosen over bundled Piper and Kokoro voices in the §6 voice test |
| Server | Laptop, thin: ledger, opportunities, pay links, WhatsApp, events; no AI |
| Reachability | Cloudflare Quick Tunnel by default; named tunnel if the team has a domain on Cloudflare |
| Pay links | Expire 1 hour after creation (`PAY_LINK_TTL_MIN`, default 60) |
| WhatsApp | Sent by the server with the Meta Cloud API; token never leaves the laptop |
| Release | Debug-signed APK attached to a GitHub Release by a tag-triggered workflow |

## 3. Facts this design rests on (checked 2026-10-25)

| Fact | Source | Consequence |
|---|---|---|
| `litert-community/gemma-4-E4B-it-litert-lm` is Apache-2.0, 3.66 GB; on S26 Ultra GPU: 0.8 s to first token, ~22 tok/s decode (~46 with speculative decoding), ~0.7–1 GB CPU memory | Hugging Face model card | Fits an 8 GB phone; too big for the APK and for a GitHub release asset (2 GB limit), so it downloads from Hugging Face |
| LiteRT-LM Kotlin: `com.google.ai.edge.litertlm:litertlm-android`; `Content.ImageBytes` for images; GPU needs `libOpenCL.so` / `libvndksupport.so` declared as optional native libraries; no constrained-JSON API | LiteRT-LM Android guide | We ask for JSON in the prompt, parse the first object, validate, and fall back |
| sherpa-onnx `tts-models` has Piper Hindi voices `hi_IN-pratham`, `hi_IN-priyamvada`, `hi_IN-rohan` (medium; int8 ≈ 21 MB, fp16 ≈ 36 MB, fp32 ≈ 67 MB) | sherpa-onnx release assets | Piper Hindi is officially available offline |
| sherpa-onnx `kokoro-multi-lang-v1_0` lists Hindi speakers (31 `hf_alpha`, 32 `hf_beta`, 33 `hm_omega`, 34 `hm_psi`) but documents only English and Chinese text support; int8 v1_1 ≈ 147 MB | sherpa-onnx Kokoro docs | Kokoro Hindi is a test candidate, not a promise (§6) |
| sherpa-onnx v2.0.0 was released 2026-10-23 | GitHub releases | Pin the newest release that is at least two weeks old |
| Quick Tunnels need no account, change hostname on every restart, allow 200 concurrent requests and **do not support Server-Sent Events** | Cloudflare TryCloudflare docs | The app gets events by long-poll (§5.4); SSE stays for local web pages |
| Meta Cloud API: free-form text only inside the 24-hour customer service window; business-initiated messages need an approved template. India utility template ≈ ₹0.115–0.145 per message (secondary sources only) | Meta get-started docs; pricing blogs | Template first, free-form fallback (§5.5); the deck price must be checked against Meta's rate card |

## 4. Architecture

```
 Merchant's phone (Munshi app)                 Laptop server (Node)            Customer phone
 ─────────────────────────────                 ────────────────────            ──────────────
 Brain (Gemma 4 E4B, LiteRT-LM)                 ledger · khata · opportunities
   Phraser · Answerer · ShelfReader             pay links (1 h) · WhatsApp
 Voice (Android TTS hi-IN)                     events ring buffer
 Conductor (unchanged flow)                     no AI, no paid API
        │  HTTPS + x-demo-key                          │
        └────────── Cloudflare tunnel ─────────────────┤◀──── /pay/:ref (public) ◀──── opens link
```

The phone sends the server decisions (confirm, decline, propose, shelf readings), never prompts. The
server sends the phone facts and template lines, never model output. Customer names and amounts reach the
phone's model; payer hashes never leave the server (as today).

## 5. Server changes

### 5.1 Claude removed
- Delete `@anthropic-ai/sdk`, `makeAnthropic`, `AnthropicLike`, `deps.anthropic`, `MODEL`, the agent loop in
  `agent.ts` and the vision call in `shelf.ts`.
- `phraseFull` keeps only the template and the hand-written Devanagari.
- `converse` keeps `classifyReply`, so yes and no on the pending proposal still work. Anything else returns
  `NOT_UNDERSTOOD` (the web stage fallback lives with this).
- A WhatsApp image now gets the announcement "Shelf ki photo Munshi app se kheenchiye."
- `POST /api/shelf` (image) is removed. `/api/control/shelf` (canned beat) stays.
- The README env table loses the Claude rows.

### 5.2 New endpoints (all behind the demo key)
| Endpoint | Returns / does |
|---|---|
| `GET /api/facts` | `{ shop, khata: [{khataId, name, amount, days}], tasks: [{id, type, line, amount}], week: {total}, inventory: [{sku, name}] }`. Never selects `payer_hash`. |
| `POST /api/propose` `{kind: 'udhaar_reminder', khataId}` | Same rules as today's `send_udhaar_reminder` tool. Returns `{opportunityId, line, spoken, display}`. 404 for an unknown khata, 409 if already sent. |
| `POST /api/shelf/readings` `{readings: [{sku, level}]}` | Runs `shelfFlow` (unknown SKUs and levels dropped). Returns the same shape the old `/api/shelf` returned. 422 if nothing usable is left. |
| `GET /api/events/poll?after=<seq>` | Long-poll: returns `{events: [{seq, ...event}], seq}` as soon as any event after `seq` exists, else after 25 s with an empty list. Server keeps the last 200 events in memory. `reset` clears the buffer. A client with a `seq` older than the buffer gets `{resync: true}`. |

### 5.3 Pay links expire
- `pay_links` gains `expires_at`. The migration runs in `openDb`: if `PRAGMA table_info` shows no column,
  `ALTER TABLE ... ADD COLUMN`. The status CHECK is unchanged; "expired" is derived as
  `status='pending' AND expires_at <= now()`, using the demo clock, so `/control` "advance 61 min" expires a link on stage.
- `GET /api/pay/:ref` adds `status: 'expired'`. The pay page (`web/Pay.tsx`) then shows "Yeh link khatam ho gaya. Ramesh ji
  se naya link maangiye." and no Pay button.
- `POST /api/pay/:ref` on an expired link returns 410 `{error: 'expired'}`. No ledger row, no event.
- Every minute, and on each `next` and `brief`, the server checks for links that expired since the last
  check. For each one it publishes `{type: 'expired', ref, name, amount}` and reopens the khata's
  opportunity with `payload.reason = 'link_expired'`. The template becomes "{name} ka link khatam ho gaya,
  ₹{amount} abhi baaki. Naya link bhejun?" with a matching Devanagari line. Confirming makes a fresh link.

### 5.4 Events over the tunnel
SSE (`/api/events`) stays for local web pages. The Android app switches to the long-poll endpoint because
Quick Tunnels drop SSE. It keeps the same `SoundboxEvent` types plus `expired`.

### 5.5 WhatsApp
- If `WA_TEMPLATE` is set (e.g. `udhaar_reminder`, a utility template with body parameters name, amount
  and link), send a template message. Otherwise send today's free-form text, which works only inside the
  24-hour window. For the demo, the team phone (`DEMO_PHONE`) messages the test number first.
- The send result line is unchanged: sent → "reminder bhej diya"; not sent → "Yeh QR … dikha dijiye", with
  the QR on screen.

### 5.6 Tunnel start script
`npm run tunnel` (`scripts/tunnel.ts`):
1. Fails with an install hint if `cloudflared` is missing (`winget install Cloudflare.cloudflared`).
2. Generates a `DEMO_KEY` if none is set.
3. Starts `cloudflared tunnel --url http://localhost:$PORT` and reads the `https://*.trycloudflare.com`
   address from its output.
4. Starts the server with `PUBLIC_URL` set to that address, then prints the `/control` link.

The phone re-pairs by QR after each restart. With `TUNNEL_NAME` and `PUBLIC_URL` set, it runs a named
tunnel instead and the address stays fixed.

## 6. Hindi voice

**Outcome (2026-10-25): Android TTS hi-IN.** The team ran the voice test below on a Galaxy A34 and chose
the phone's own Google Hindi voice. Nothing is bundled; the sherpa-onnx engine was removed again; the app
speaks through `AndroidSpeaker` as before. Details: `docs/superpowers/notes/2026-10-voice-test.md`. The rest
of this section is the test as designed.

**Voice test first.** A hidden "Awaaz jaanch" screen (long-press the week chip) speaks five real lines. They
include the brief greeting, an udhaar question with ₹450, a payment announcement and a shelf line, all in
Devanagari from `spoken`. Candidates:

1. Android TTS hi-IN (today's baseline)
2. Piper `hi_IN-pratham`, `hi_IN-priyamvada`, `hi_IN-rohan` (fp16)
3. Kokoro `hf_alpha` and `hm_omega` through sherpa-onnx, **only if** it speaks Devanagari correctly. If
   sherpa-onnx's text front end can't handle Hindi (its docs cover only English and Chinese), the screen
   marks Kokoro "not supported" and the test goes on without it.

For each candidate the screen shows time to first audio and real-time factor. Test models are pushed with
`adb` into app storage, not bundled. The team listens on the 8 GB phone and picks one voice.

**After the choice:**
- The chosen model (with `espeak-ng-data`) is bundled under `assets/voice/`, arm64-v8a only.
- `SherpaSpeaker` implements the existing `Speaker` interface; `AndroidSpeaker` stays as the fallback.
- The FIFO queue is unchanged. Known lines (brief items, announcement templates, "Theek hai") are
  synthesised ahead while idle and cached as PCM per line.
- Lines always speak the Devanagari `spoken` text. If a line has none, use `toSpoken`. If even that
  fails, use the Android TTS en-IN voice on the Roman line.

## 7. On-device brain (Android)

New package `brain`, with one interface the Conductor depends on and a fake for tests:

```kotlin
interface Brain {
    val ready: StateFlow<BrainState>              // Missing, Downloading(pct), Loading, Ready, Failed
    suspend fun reword(line: String, spoken: String): Phrased?     // null → use the template
    suspend fun answer(question: String, facts: Facts): BrainReply? // null → NOT_UNDERSTOOD
    suspend fun readShelf(jpeg: ByteArray, items: List<Sku>): List<Reading>? // null → "Photo saaf nahi dikhi"
}
data class BrainReply(val say: String, val spoken: String?, val propose: Proposal?)
```

- **`GemmaBrain`** wraps a LiteRT-LM `Engine` loaded once in `MunshiService`: GPU backend with speculative
  decoding, CPU if GPU init fails. One conversation per call, so no history leaks between customers.
  Calls run one at a time.
- **Prompts** move from the server unchanged in spirit. Reword: the `REWRITE` prompt, two lines (Roman,
  then Devanagari). Answer: the `SYSTEM` prompt plus `facts` as JSON; reply with one JSON object
  `{"say", "spoken", "propose": {"kind":"udhaar_reminder","khataId"} | null}`. Shelf: today's shelf prompt
  with the SKU list.
- **Guards (pure Kotlin, unit tested):**
  - Reword keeps every number from the template; the Roman line has no Devanagari.
  - An answer may only contain numbers that appear in `facts`, and `khataId` must be in `facts.khata`.
  - Shelf readings are filtered to known SKUs and levels.
  - Any guard failure gives `null`.
- **Time limits:** reword 6 s, answer 12 s, shelf 20 s. The brief's three items are reworded in the
  background as soon as the brief loads; each line speaks the reworded text if it's ready, else the
  template. Cache by template text.
- **Conductor wiring:** `Answer.Text` → `api.facts()` → `brain.answer`. If `propose` is set, then
  `api.propose`, say `say`, then ask the proposal like any other. Shelf: camera → `brain.readShelf` →
  `api.shelfReadings`. When the brain is not `Ready`, everything uses today's template paths.
- **Model download (`ModelStore`):**
  - Android `DownloadManager` from the Hugging Face resolve URL, pinned to a repo commit so the file can't
    change under us, into `getExternalFilesDir("models")`.
  - Wi-Fi only by default; resumable; SHA-256 checked against a constant; at least 4.5 GB free required.
  - The status line shows "Munshi ka dimaag aa raha hai · 1.2 / 3.7 GB".
  - For the stage phone, `adb push` to the same path skips the download.
  - Devices under 7 GB RAM get E2B (2.58 GB) instead; under 4 GB, templates only.

## 8. Release

`.github/workflows/release.yml`:
- Runs on tags `v*`, and by `workflow_dispatch` as a dry run that only uploads an artifact.
- Builds `assembleDebug` with JDK 21 (as CI does) and renames the APK to `munshi-<tag>-debug.apk`.
- Runs `gh release create <tag>` with the APK and a `.sha256`.
- Release notes cover:
  - installing the APK (allow unknown sources)
  - the first-run model download (3.7 GB, Wi-Fi)
  - pairing with `npm run tunnel` and the `/control` QR
  - that payments are simulated
- Signing uses a committed `android/keystore/munshi-debug.jks` (debug key, public password `android`), so
  each release installs over the last one. It's labelled "not for Play Store". The default per-machine
  debug key would force an uninstall between releases.
- The first release, `v0.2.0`, is tagged once all plans below are merged and the rehearsal passes.

## 9. Failure handling

| Failure | Behaviour |
|---|---|
| Model missing or downloading | Template lines; questions → "Haan ya Nahi dabaiye"; shelf → "Abhi photo nahi dekh sakta" + `/control` beat |
| GPU init fails | CPU backend (slower; the time limits still hold) |
| Brain timeout or guard failure | Template / `NOT_UNDERSTOOD` / "Photo saaf nahi dikhi" |
| Low memory kill of the engine | Service reloads it on next use; templates meanwhile |
| Chosen voice fails to load | Android TTS hi-IN, then en-IN, then captions |
| Tunnel restarts (new URL) | App shows "Jud raha hai…"; re-pair by QR |
| Long-poll `resync` | Re-fetch brief and pending state, like a reconnect |
| Link expired | QR card clears, Munshi says the expiry line, re-offer comes next |
| WhatsApp template rejected / not configured | Free-form text; if not sent, QR on screen (today's path) |

## 10. Testing

- **Server (`node:test`):**
  - No `@anthropic-ai/sdk` import remains.
  - `/api/facts` shape, with no `payer_hash`.
  - `/api/propose`: success, unknown khata, already sent.
  - `/api/shelf/readings` filtering and 422.
  - Expiry: GET shows expired, POST gives 410, advancing the clock 61 min publishes `expired` and re-offers.
  - Long-poll: immediate return, 25 s empty, `resync`, `reset` clears.
  - WhatsApp template payload.
  - Migration on an old database file.
- **Android JVM:**
  - Guards (table tests).
  - Conductor with `FakeBrain`: reword used when ready, template when not; propose path; shelf path; brain
    timeout; `expired` event clears the QR.
  - Long-poll client with MockWebServer.
  - `ModelStore` checksum and resume.
  - Paparazzi for the download status line and the expired state.
- **On device (manual, recorded in the plan):**
  - Voice test results for each candidate.
  - Gemma timings for reword, answer and shelf on the 8 GB phone.
  - A full rehearsal through a Quick Tunnel with a second phone paying, and one link expiring.
- **CI:** unit and screenshot tests as today; release workflow dry run on `workflow_dispatch`.

## 11. Build order

Four plans, on a new branch `feat/on-device-ai` cut from `feat/app-redesign`:

| Plan | Work | Done when |
|---|---|---|
| (d) Thin server | §5 | Server tests green; tunnel script pairs the emulator through a Quick Tunnel |
| (f) Voice | §6 | Done 2026-10-25: voice test run on the phone; Android TTS chosen, nothing bundled |
| (e) Brain | §7 | Gemma rewords, answers and reads a shelf on the phone within the time limits |
| (g) Release | §8 | Dry run green; `v0.2.0` published after the rehearsal |

(f) depends only on the existing app, so it can run before or alongside (d). (e) needs (d)'s endpoints.
(g) comes last.

## 12. Cost story (for the deck)

| Item | Cost per merchant |
|---|---|
| AI | ₹0 per request; runs on the merchant's own phone. One-time 3.7 GB download (E2B: 2.6 GB) over Wi-Fi |
| Voice | ₹0; the phone's own Android TTS, no download |
| Tunnel / server | ₹0 for the demo (Quick Tunnel, laptop). At scale, one small VM serves many shops because it only stores rows and sends links |
| Payments | In production, confirmation comes from Paytm's own rails |
| WhatsApp | The one cost that grows: ≈ ₹0.115–0.145 per utility reminder in India (secondary sources; confirm on Meta's rate card before it goes in the deck) |
