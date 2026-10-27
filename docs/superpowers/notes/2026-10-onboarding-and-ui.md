# Onboarding and redesign on the phone, 2026-10-26, Samsung Galaxy A34 (SM-A346E, 8 GB)

Build: `feat/on-device-ai` at the onboarding fixes commit. Local server through `adb reverse tcp:3000`.
The model stayed in `files/models`; only the onboarding and pairing DataStores were cleared, and
notifications were revoked, to start onboarding fresh.

## Checked

| What | Result |
|---|---|
| Welcome → Connect (typed `http://localhost:3000`) → Number | Pairing moved on by itself; the dock rose above the keyboard |
| Number 9876543210 → OTP fills after ~1 s → Shop | Lookup returned the demo shop with the phone |
| Shop: "Munshi aapko kya bulaye" → Suresh bhai, saved | Server profile updated (`addressAs`, `phone`) |
| Permissions | Mic already granted ("Mil gayi"); Notifications via *Dein* → system dialog → granted |
| Brain | "Gemma 4 · E4B", green bar, "Taiyaar" (model already on the phone) |
| Voice check | Recogniser dropped online (error 11) on one try; "“Haan” sunaai nahi diya" with *Dobara* shown |
| Finish → counter screen | "Shubh prabhat, Suresh bhai"; kept after a force-stop and restart |
| Settings → Dukaan ki jaankari → shop name "Gupta General Store" | Saved; the counter screen came back; Settings shows the new name |
| End-to-end script (session, in-question query, Pinky proposal, shelf) | Same flow as before the redesign; shelf counts 6 / 2 / 1, Surf group order asked |
| Two pay links | Both in the stack; the newest in front |

The WhatsApp text with the saved shop name is covered by `server/test/profile.test.ts`. WhatsApp is off on
the local server, so it was not sent from the phone.

## Found and fixed on the phone

- **A cold start reset onboarding.** The saved pairing loads a moment after the app starts, and the flow
  took that moment for a cleared pairing. It sent the merchant back to Connect, and from a finished
  onboarding too. Now `MunshiApp.paired` stays unknown until the pairing is read.
- **The voice check heard Munshi itself.** It accepted "मैं" from its own greeting. It now passes only on
  a haan, and gives anything else one more listen.
- **Stale names after a shop edit.** The counter screen now reloads the profile each time it opens.
- **The QR stack kept its place on the older card when a new link came in.** It now jumps to the
  newest card.
- **OTP boxes on narrow phones.** The six boxes were clipped on a 393 dp screen; they are now 44 dp wide.

## Differences from Figma, kept on purpose

- The ask block shows the server's sentence split ("Sharma ji ka / ₹450 / udhaar 12 din se…") rather
  than a name over the full spoken line; on the phone it reads as one sentence.
- Editing the shop from Settings shows the Shop step with its "3 / 6" progress bar.
