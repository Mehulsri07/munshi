# Quick Tunnel rehearsal, 2026-10-25

**Setup:** `npm run tunnel` with `PAY_LINK_TTL_MIN=2`, a Cloudflare Quick Tunnel (`*.trycloudflare.com`),
WhatsApp off. Soundbox: Samsung Galaxy A34 (SM-A346E), debug build from `feat/on-device-ai`, paired with the
tunnel address (no `adb reverse`, so every request crossed Cloudflare). Customer: the public pay page in a
desktop browser.

| # | Check | Result |
|---|---|---|
| 1 | Pair through the tunnel; "Jud raha hai…" clears | Pass: live 2 s after launch (long-poll) |
| 2 | Dukaan kholo → brief → Haan on Sharma ji → QR | Pass: QR for ₹450 shown; Munshi moved on to the next task |
| 3 | Customer pays on the public page → stamp + chime | Pass: stamp, "Sharma ji ne ₹450 chuka diye" banner and week chip ₹450 within 2 s of paying |
| 3b | `/api/propose` (as the phone's model would) → nudge → Munshi asks | Pass: "Pinky didi ka ₹230 … Reminder bhej doon?"; Haan → QR (WhatsApp off, so the QR fallback); her link was then paid from a phone, stamp + ₹680 |
| 4 | Unpaid link expires | Pass: Verma ji's link expired 16:52:31, swept 16:52:46; QR cleared on the phone; the pay page shows "Yeh link khatam ho gaya"; paying it returns 410. The 6.5 s banner had gone before the screenshot, so the spoken announcement was not observed |
| 5 | Re-offer after expiry | **Bug found and fixed** (see below) |
| 6 | Stop the tunnel and server → restart → re-pair | Pass: "Jud raha hai…" with Haan/Nahi disabled while down; after restart and re-pair the app was live with a fresh brief in 6 s |

**Bug (fixed in d252662):** a reminder proposed for a debt under 7 days old (Verma ji, 3 days) lost its
re-offer. The sweep re-opened the task, but the next refresh deleted it because the overdue detector didn't
cover it. A khata whose link ran out unpaid now stays on the list whatever its age; there's a regression
test in `server/test/expiry.test.ts`. Re-checked live after the fix: Verma ji's link expired 16:58:52, was
swept 16:58:58, and her task is back as `new` with `relink: true`.

**For plan (e):** `/api/facts` lists only the top five open tasks by value. With seven tasks worth more than
₹120 open, Verma ji's re-offer was not in it, so the phone's model would not mention it. Consider always
including relink tasks in `facts.tasks`.

**Notes for the stage:**
- **Re-pairing after a tunnel restart:** use the `/control` QR (long-press the Munshi wordmark). The adb
  `--es url` shortcut only works on a cold start (`am force-stop` first), because `MainActivity` reads the
  extras in `onCreate` only.
- **Nudge order:** "Munshi, aur kuch?" asks the highest-value open task, so a small re-offer (₹120) waits
  behind the ₹50,000 loan. The expired link's re-offer still appears in `/api/facts` and in the next
  brief or nudge once the bigger tasks are answered.
- **Restarts re-seed:** every server restart resets the demo data, including the week total.
