# Munshi onboarding and counter-screen redesign

**Date:** 2026-10-26 · **Branch:** `feat/on-device-ai` · **Figma:** https://www.figma.com/design/W68m8BYgxNJRF7sy8u81s1
(pages Foundations, Components, Onboarding, Counter screen, Sheets; reviewed and approved 2026-10-26)

## 1. Goal

Make the Android app feel calm and obvious to a shopkeeper who has never seen it, and let a merchant set
Munshi up for their own shop in a few minutes.

- **Onboarding** personalises the demo shop: connect to the server, identify through a (simulated) Paytm for
  Business number, confirm the shop's details, grant the mic and notifications, start the Gemma download,
  and check the voice. The seeded demo data stays; it now carries the merchant's names.
- **Counter screen** shows only what the merchant can do right now. Haan / Nahi appear only while Munshi
  asks. Boliye has a mic icon everywhere. Open pay links stack instead of replacing each other.
- **Sheets** for udhaar (new), hisaab (restyled) and settings (new) open from the top bar.

Not in scope:
- a real Paytm lookup or OTP
- creating a second, empty merchant
- bottom tabs
- dark mode
- the web page's look

## 2. Decisions (made with the user)

| Question | Decision |
|---|---|
| What onboarding sets up | Personalise the seeded demo shop |
| Identity | Paytm for Business number, then an auto-filled OTP; both labelled "Demo" on screen |
| Look | Refine the ledger language: paper, Fraunces and Inter, red accent, ink dock; fewer lines, more space |
| Structure | One counter screen plus bottom sheets; no tabs |
| Icons | Line icons, with the mic on every Boliye; camera on Shelf photo; check on Haan |

## 3. Visual language

The design system lives in Figma (Foundations and Components pages). It is the source for these changes.

**Tokens.** `design/tokens.json` gains four colours, generated into `Tokens.kt` as today:

| Token | Value | Use |
|---|---|---|
| card | `#FFFDF8` | cards and sheets' inner panels |
| redSoft | `#F3DED8` | icon badges |
| ok | `#2E7D4F` | ready, granted, paid, "Theek" |
| warn | `#B7791F` | downloading, demo notes, "Kam" |

Translucent tints (demo notes, level pills) are the base colour at 12–14 % alpha. The red margin line
stays. The ruled lines are dropped from the page background.

**Type.** These are the Figma text styles:
- Display/Greeting: Fraunces SemiBold 34/40
- Display/Rupee: Fraunces SemiBold 76, −3.5 %
- Title/Who: Fraunces SemiBold 22
- Body/Spoken: Fraunces Italic 18/25
- Body/Regular: Inter 15/22
- Body/Small: Inter 13/18
- Label/Kicker: Inter Bold 10, +16 %, upper case
- Label/Button: Inter SemiBold 17
- Label/Chip: Inter SemiBold 12

**Icons.** 24 dp line icons with a 1.8 stroke and round caps, drawn as Compose `ImageVector`s from the
same SVG paths as Figma: Mic, Camera, Check, Close, Store, Phone, Bell, Spark, Qr, ChevronRight, Wifi,
Speaker, Rupee.

**Components** (each one maps to one composable):

| Figma | Compose | Notes |
|---|---|---|
| Button / Primary | `MunshiButton(Primary)` | Red gradient, 56 dp tall, radius 18, optional leading icon. The one thing to do now |
| Button / Secondary | `MunshiButton(Secondary)` | Ivory outline at 40 % |
| Button / Tool | `MunshiButton(Tool)` | 44 dp pill, ivory at 10 %, icon plus label (Boliye, Shelf photo) |
| Mic button | `MicButton(listening)` | 72 dp red circle in an 88 dp slot; listening adds a 3 dp red-on-ink ring |
| Status dot | `StatusDot(status)` | 10 dp: ok = connected and AI ready, warn = model downloading or loading, ink3 = reconnecting |
| Top bar | `TopBar` | "Munshi" wordmark and status dot (tap: settings); week chip (tap: hisaab) |
| QR card | `QrCard(state)` | Open (QR, name, ₹, "Link N min tak chalega"); Paid (faded QR, red "Chuka diya" stamp, green note); Expired (QR at 18 %, red note, "Naya link bhejein →") |
| Task row | `TaskRow` | Kicker, who, ₹ on the right, hairline below |
| Field | `FieldRow` | Small label, 18 sp value, one underline; editable |

## 4. Onboarding

Eight screens, all with the same frame: the margin line, a progress bar (6 steps after Welcome, the current
step longer), a kicker, a Fraunces title, short body text, and the ink dock at the bottom holding the
step's single primary action.

| # | Screen | Content | Action |
|---|---|---|---|
| 1 | Welcome | Wordmark, italic tagline, a sample spoken line, three benefits with icons (Bell, Rupee, Mic) | *Shuru karein* |
| 2 | Connect (1/6) | "Munshi se jodiye": scan the `/control` QR; "Ya server ka pata likhiye" opens a URL field | *QR scan kijiye* (camera icon) |
| 3 | Paytm number (2/6) | Field "Mobile number", warn note "Demo: koi bhi number chalega" | *OTP bhejo* |
| 4 | OTP (2/6) | Six boxes filled automatically after 1.2 s, ok note "Demo: OTP apne aap bhara gaya" | *Aage* |
| 5 | Shop (3/6) | "Yeh aapki dukaan hai?" Five editable fields from the lookup: Dukaan ka naam, Ilaaka, Kaam, Aapka naam, Munshi aapko kya bulaye (red italic) | *Haan, sahi hai* (saves) |
| 6 | Permissions (4/6) | Mic and Notifications cards, each with *Dein*, which becomes "Mil gayi" | *Aage* (enabled always; missing grants are asked again on first use) |
| 7 | Brain (5/6) | Model name for this phone (E4B or E2B), progress, size, Wi-Fi note, "Tab tak Munshi bina AI ke kaam karega" | *Aage badhein*; the download continues |
| 8 | Voice check (6/6) | Munshi says "Namaste {addressAs}, main Munshi hoon. Sun pa rahe hain? Haan boliye.", then listens once; "Aapne kaha: …" with a check when haan is heard | *Taiyaar! Dukaan kholiye*; "Baad mein" skips |

**State.**
- The current step and `done` are kept in a DataStore named `onboarding`.
- The app resumes at the saved step.
- Connect saves the pairing config as today, so steps 3–8 use that server.
- Settings → "Onboarding phir se" sets `done = false` and the step back to Welcome; it keeps the pairing.
- If the pairing is cleared, the app returns to Connect.

**Errors.**
- The lookup or save fails: the error text shows under the action ("Server se baat nahi hui. Dobara
  koshish kijiye.") and the action stays.
- An invalid number (not 10 digits after `+91`): "10 ank ka number daaliye."
- The voice check hears nothing: "Kuch sunaai nahi diya" plus *Dobara* and *Baad mein*.

## 5. Counter screen

Same layout in every state:
- the status bar inset
- `TopBar`
- the content column (40 dp from the left, 340 dp wide on a 412 dp phone, scaling as today)
- the floating ink dock, 10 dp from the edges and above the navigation bar

| State | When | Page | Dock |
|---|---|---|---|
| C1 Morning brief | shop closed | Date kicker, "Shubh prabhat, {addressAs}", "Aaj ke N kaam se ₹X aa sakte hain.", up to 3 `TaskRow`s | *Dukaan kholo* (Store), then Boliye and Shelf photo |
| C2 Asking | `ask != null` | Speaking kicker, who, ₹ figure, italic line, tags, hint "Kuch aur poochna ho toh Boliye dabaiye" | Status row ("Munshi pooch raha hai", "n / 3"), Haan (fill) and Nahi (130 dp), tools |
| C3 Listening | `mode == Listening` | As C2 (or as C5), plus a live-caption card | Waveform and "Sun raha hoon…" (the listen ends by itself after its window) |
| C4 Thinking | `mode == Working` after free speech | "Aapne poocha" with the question in large italic, three dots, "Ek second, dekhta hoon" | "Soch raha hoon… phone par, muft", thin progress, tools at 45 % |
| C5 Open, nothing to ask | shop open, no question | "Dukaan khuli hai", "Sab theek hai. Munshi sun raha hai.", a tally ("₹X is hafte", "N khule QR", "₹Y udhaar baaki", the last opening the Udhaar sheet), QR stack | Shelf (left), `MicButton` (centre, labelled Boliye), *Band karo* (right) |
| C6 Shelf result | after a shelf reading | "Shelf dekh li", the headline, a card listing each SKU with its count and a Theek / Kam / Khatam pill, then the offer | As C2, with *Haan, judo* |

**QR stack.**
- Every pay link the app shows becomes a card in a horizontal pager, newest first, with dots above.
- When a card is paid, its state becomes Paid (stamp and chime) and the card leaves after 4 s.
- When a link expires, its state becomes Expired. Tapping "Naya link bhejein" removes the card and asks the
  server's next task once (the server has already re-offered the reminder).
- This replaces the single `payQr`.

**Status dot.** Grey when not live; amber while the model is downloading or loading; green otherwise.

## 6. Sheets

Modal bottom sheets on paper, with a 24 dp top radius, a handle, and a 42 % ink scrim.

- **Udhaar** (from the "udhaar baaki" tally in C5):
  - each open khata from `/api/facts`: name, "N din se baaki", ₹
  - *Yaad dilao* on each row closes the sheet, proposes that reminder (`/api/propose`) and asks it
    (the same path as a spoken request)
- **Hisaab** (week chip): the existing content, restyled with kicker lines; the loan in its own panel,
  "Kamaai mein nahi gina".
- **Settings** (wordmark), one row each, with a chevron:
  - Dukaan ki jaankari: opens the onboarding Shop step as a single screen and saves
  - Munshi ka dimaag: model state, read-only
  - Server se juda hai: the URL
  - Awaaz: read-only
  - Dobara jodiye: clears the pairing
  - Onboarding phir se

## 7. Server changes

**Table.** A new table `profile (merchant_id PK, shop_name, locality, category, owner_name, address_as,
phone, updated_at)`. It is not in the seed's `TABLES`, so `/api/control/reset` keeps it.

**Reading the profile.** `getProfile(db, id)` returns the row. With no row, it builds the profile from
`merchants`, using owner "Ramesh Gupta" and address "Ramesh ji".

**Endpoints** (all behind the demo key):

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/onboard/lookup` | `{phone}` | `{profile}`: the current profile with this phone (the simulated Paytm lookup). 400 `bad_phone` unless the number is 10 digits after an optional `+91` |
| GET | `/api/merchant` | — | `{profile}` |
| POST | `/api/merchant` | `{shopName, locality, category, ownerName, addressAs, phone?}` | `{profile}`. 400 `bad_profile` if `shopName` or `addressAs` is empty or any field is over 60 characters |

**Using the names:**
- `getFacts().shop` is `profile.shopName`, and facts add `addressAs`.
- The WhatsApp reminder text uses `profile.shopName` in place of "Ramesh Kirana".

## 8. Android changes

**API.** `Profile` and `MunshiApi.lookup(phone)`, `merchant()`, `saveMerchant(p)`.

**Conductor and UiState:**
- `profile` is loaded on `start()`; the greeting is "Suprabhat {addressAs}! Aaj ke kaam sunaata hoon."
- `payQrs: List<PayQr>` with `state` (Open, Paid, Expired) replaces `payQr` and `stamp`.
- `asked: String?` is the free-speech question shown in C4.
- `khata: List<FactKhata>` comes from `/api/facts`, refreshed with the ledger; it feeds the tally and the Udhaar sheet.
- `shelf: List<ShelfLine>?` holds the name, count and level from the last reading. It is cleared on the
  next question. `Reading` gains an optional `count`.
- New functions:
  - `remind(khataId)`
  - `closeShop()` (the *Band karo* button)
  - `relink(ref)`
  - `voiceCheck(addressAs): String?`, which returns what was heard

**UI:**
- New components: Icons, MunshiButton, MicButton, StatusDot, TopBar, QrCard (three states), QrStack,
  TaskRow, FieldRow, and the Sheet shell.
- `MainContent` is rewritten around the six states.
- New `OnboardingFlow` with the eight steps.
- New `UdhaarSheet` and `SettingsSheet`.
- `HisaabSheet` is restyled.
- `HaanButton`, `NahiButton` and the ruled background are removed.

**Routing in `MainActivity`:**
- Onboarding not done: `OnboardingFlow`.
- Otherwise, no pairing: `PairScreen`.
- Otherwise: `MainScreen`.

The permission request moves out of `onCreate` into onboarding step 6. A missing grant is asked again when
the mic or notifications are first needed.

## 9. Testing

- **Server (`node:test`):**
  - profile defaults and save
  - lookup phone validation
  - reset keeps the profile
  - facts and the WhatsApp text use the saved shop name
  - a database file made before this change opens
- **Android JVM:**
  - `Onboarding` state holder with a fake API and an in-memory store: step order, resume, lookup error,
    save, redo
  - Conductor:
    - the greeting uses the profile
    - the QR stack: add, paid then removed after 4 s, expired then relink
    - `remind`
    - shelf lines
    - `voiceCheck`
  - Contract fixtures for the new JSON.
- **Paparazzi:** every onboarding screen, C1–C6, the three sheets, and the components sheet, compared by
  eye with the Figma frames before recording.
- **On the phone:**
  - onboarding from a fresh install, with the names showing in the greeting and the WhatsApp text
  - the end-to-end script still passes
  - two QRs open at once
