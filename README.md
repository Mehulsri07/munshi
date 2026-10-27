# Munshi

The Soundbox that runs the shop. Munshi spots money a kirana is leaving on the table, proposes one action in one Hinglish line, does it only on the merchant's "haan", and proves the result in rupees.

All data is synthetic demo data. No real money moves.

## Run

```bash
npm install
npm run build
npm run server
```

Open http://localhost:3000. Routes:

| Route | Who | What |
|---|---|---|
| `/soundbox` | merchant (phone or tablet on the counter, or a projector) | the device face: speaks, listens, Haan / Nahi, shelf photo |
| `/pay/:ref` | customer | mock UPI page opened from the WhatsApp link or QR |
| `/ledger` | merchant | rupees Munshi earned this week, each traced to its action; loan shown separately |
| `/paytm` | judges | the demo week projected across 1 to 10 lakh merchants (labelled synthetic) |
| `/control` | team, offstage | fire the finale beats on cue |

`npm test` runs the server test suite. `npm run dev` runs Vite on 5173 with `/api` proxied to 3000 (start the server too).

### App API (used by the Android Soundbox app)

| Call | Returns |
|---|---|
| `GET /api/brief` | `{ items: [{ id, type, title, amount }], total }`: today's top three tasks, read-only |
| `POST /api/next` | `{ id, type, line, spoken, display: { who, amount, rest } }` |
| confirm / decline / converse | the result `line` plus `spoken` (Devanagari) when known |
| `GET /api/events` (SSE) | `announce` gains `spoken`; `paid` gains `name`, `amount`; `shelf.next` gains `spoken`, `display` |
| `GET /api/facts` | shop name, open khata (`khataId`, name, amount, days), the top five open tasks plus any re-offered reminder, each with its template `line` and Devanagari `spoken`, the week's total and the shelf SKU list, for the phone's on-device model. Never contains payer identities |
| `POST /api/propose` `{ "kind": "udhaar_reminder", "khataId": 3 }` | the model suggests a reminder: `{ opportunityId, line, spoken, display }` for Munshi to ask. 404 unknown khata, 409 already sent |
| `POST /api/shelf/readings` `{ "readings": [{ "sku": "surf_1kg", "level": "empty" }] }` | what the model saw on the shelf; unknown SKUs and levels are dropped. Same result as the shelf beat; 422 if nothing usable is left |

`spoken` is the same line in Devanagari for a Hindi voice; when it is missing, speak `line` with an Indian English voice. Design tokens live in `design/tokens.json`; run `npm run tokens` after editing them. `/control` shows a pairing QR (`{"url","key"}`) that the app scans.

## Docker and CI

```bash
docker build -t munshi .
docker run -p 3000:3000 --env-file .env munshi
```

The image runs one server process. Run exactly **one** container: live announcements are an in-memory broadcast and the data is a SQLite file at `/data/munshi.db`, re-seeded on every boot, so it needs no volume. `GET /healthz` returns `{"ok":true}` without the demo key; the image's `HEALTHCHECK` and any host can probe it. Pass secrets as environment variables at run time; `.env` is never copied into the image.

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to `master` and every pull request: tests, type-check and web build, then builds the image and smoke-tests the container (`/healthz`, the Soundbox page, and the first proposal).

When you pick a host, choose one that keeps a single long-running server always on (no scale-to-zero, no serverless), close to the venue for the judges' phones.

## Environment

Put these in `.env` (loaded automatically). Every one is optional; without it the demo falls back instead of failing.

| Variable | Does | Without it |
|---|---|---|
| `WA_TOKEN`, `WA_PHONE_ID` | send udhaar reminders on WhatsApp (Meta Cloud API) | the Soundbox shows a QR for the same pay link |
| `WA_GRAPH_VERSION` | Graph API version (default `v23.0`) | default |
| `WA_TEMPLATE`, `WA_TEMPLATE_LANG` | send reminders as this approved utility template (body parameters: name, amount, pay link; language default `en`) | free-form text, which only reaches numbers that messaged the test number in the last 24 h |
| `PAY_LINK_TTL_MIN` | minutes before a pay link expires (default 60; set 2 to show expiry on stage) | 60 |
| `TUNNEL_NAME` | `npm run tunnel` runs this named tunnel (needs `PUBLIC_URL`) | a Quick Tunnel with a random address |
| `WA_VERIFY_TOKEN` | verify token for the inbound webhook `/webhook/whatsapp` (a shelf photo sent there gets "Shelf ki photo Munshi app se kheenchiye.") | webhook verification fails; use the Soundbox camera or `/control` |
| `PUBLIC_URL` | public URL used in WhatsApp links and QR codes | `http://localhost:3000`, which a judge's phone cannot open |
| `DEMO_CUSTOMER_PHONE` | Sharma ji's WhatsApp number, E.164 (e.g. `+9198…`) | a dummy number |
| `DEMO_KEY` | required on the merchant and control API when set; open each screen once with `?key=<DEMO_KEY>` (stored in the browser). The customer pay page never needs it | API open to anyone who has the tunnel URL |
| `PORT`, `MUNSHI_DB` | port (3000) and SQLite file (`munshi.db`) | defaults |

### Public URL

Phones must reach the server. One command opens a free Cloudflare Quick Tunnel and starts the server behind it:

```bash
npm run tunnel
```

It prints the public address and the `/control` link (with a generated `DEMO_KEY` unless you set one). Scan the
pairing QR on `/control` with the Munshi app. Quick Tunnels need no account but get a new address on every
start (pair again) and do not carry Server-Sent Events, so the app long-polls `/api/events/poll`; the web
pages' live updates work locally. For a fixed address, create a named tunnel on a domain in your Cloudflare
account and set `TUNNEL_NAME` and `PUBLIC_URL`. Install `cloudflared` with `winget install Cloudflare.cloudflared`.

### WhatsApp test number

1. In Meta's WhatsApp dashboard, add each recipient (the judge playing Sharma ji) to the test number's allowed list.
2. Either create a utility template (e.g. `udhaar_reminder` with body `Namaste {{1}}, Ramesh Kirana se yaad dilaana tha: {{2}} baaki hain. Yahan chuka dein: {{3}}`) and set `WA_TEMPLATE`, or have the recipient message the test number first; that opens the 24-hour window for free-form text.
3. For shelf photos over WhatsApp, point the app's webhook at `PUBLIC_URL/webhook/whatsapp` with `WA_VERIFY_TOKEN`.

If any of this fails on stage, the QR on the Soundbox opens the same pay link.

## Finale script (about 5 minutes)

Open `/soundbox` on the stage device, `/control` on a laptop offstage, `/ledger` or `/paytm` on the projector. Press **Reset** in `/control` first.

1. **Shop opens.** Tap **Dukaan kholo**. The morning brief: Sharma ji's ₹450 udhaar, then the neighbourhood mithai spike, then Pinky didi. Say or tap **Haan** on Sharma ji. The WhatsApp reminder (or QR) goes out; the judge pays on their phone; the Soundbox announces *"Sharma ji ne ₹450 chuka diye"* and the week total ticks up.
2. **Shelf photo.** Tap the camera in the Munshi app (read by the phone's on-device model) or press **Shelf photo** in `/control`. Munshi says Surf Excel and Parle-G are low and offers the Surf group order: 4 shops, 8% off. **Haan.**
3. **Credit.** Press **Munshi, aur kuch?** Munshi sees the ₹72,000 Diwali stock bill due in 3 days and offers a ₹50,000 loan: true cost ₹1,240, ₹570 a day from settlements. **Haan.**
4. **Win-back.** Press **Munshi, aur kuch?** again: 12 regulars have not come for 3 weeks; a ₹15 offer goes through the Paytm app without the merchant ever seeing who they are. **Haan.** Press **Purana grahak lauta**: one returns and spends ₹340.
5. **Loyalty.** Press **Pakka grahak aaya**: a regular's 10th visit triggers the reward announcement, again without naming the customer.
6. **Close.** Show `/ledger` (every rupee traced to the action that earned it, loan kept separate), then `/paytm` at 1,00,000 merchants.

Keyboard on the Soundbox: `H` = Haan, `N` = Nahi.

By default the Soundbox only speaks up when the operator cues it, so the script order holds. Add `?idle` to the Soundbox URL to let it also offer the next thing on its own every minute when the shop is quiet (a real-shop setting, not for the stage).

## Rehearsal checklist

- [ ] Reset, then run the full script on the real stage device in Chrome, with the venue network.
- [ ] Hindi voice: the Soundbox prefers a `hi-IN` voice, then `en-IN`. Check which one the device has.
- [ ] Say "हाँ" by voice once; tap once. Both confirm.
- [ ] WhatsApp reaches the judge's phone within 5 seconds and the link opens over the tunnel.
- [ ] Ask by voice "kitna udhaar baaki hai?" (needs the phone's on-device model): a short Hinglish answer naming Pinky didi.
- [ ] Before the model has downloaded on the phone, run the brief: template lines, Haan still works.
- [ ] Drop the server's Wi-Fi mid-run: note what fails; the QR path over a phone hotspot still works.
- [ ] Record a backup video of the full run.

## How it works

- `server/detectors.ts` and the feature modules (`inventory.ts`, `customers.ts`, `credit.ts`) look at the data and emit opportunities with a rupee estimate. The brief covers shop tasks; credit and win-back come as nudges.
- `server/opportunities.ts` is the only path to a side effect: `confirmOpportunity` refuses anything not currently proposed, so a double "haan" can never send twice.
- `server/agent.ts` only classifies haan/nahi replies. The phone's on-device model suggests actions that become proposals; nothing runs without a "haan".
- Customer identity (`payer_hash`) never leaves the server; network aggregates need at least 5 shops.
- Design: `docs/superpowers/specs/2026-10-24-munshi-design.md`; plans in `docs/superpowers/plans/`.
