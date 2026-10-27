# Gemma on the phone, 2026-10-26, Samsung Galaxy A34 (SM-A346E, 8 GB)

Model: `gemma-4-E4B-it.litertlm` (3,659,530,240 bytes, pinned revision and SHA-256 in `brain/Models.kt`),
pushed with `android/scripts/push-model.ps1`. LiteRT-LM 0.16.1, GPU backend, speculative decoding on.

## Timings

| What | Time | Notes |
|---|---|---|
| Load the model | ~60 s | once per app start, in the background; the app works from templates meanwhile |
| Prefill | 45–60 tok/s | |
| Decode | 8–10 tok/s | |
| Reword a task line (background) | 8.5–21 s | while the merchant waits for nothing |
| Answer a question ("kitna udhaar baaki hai") | 10.5–15 s | 394-token prompt; was 23 s with the first 873-token prompt |
| Read a shelf photo (3 SKUs) | 22–23.5 s | |

Limits in `LlmBrain`: answer 25 s, shelf 40 s, reword 60 s (it can wait behind a question first). Munshi
says "Ek second, dekhta hoon" as soon as a question goes to the model.

## Findings from the end-to-end runs

The runs were driven from adb (`--es say|tap|shelf`, debug builds only) with a synthetic shelf photo
(6 Tata Salt, 2 Surf Excel, 1 Parle-G).

- **Warm-ups held the model.** A merchant's question queued behind a background rewording and timed out.
  Now a question cancels the warm-up (`Conversation.cancelProcess`), only one warm-up runs at a time, and
  a timed-out generation is stopped rather than left running.
- **Munshi heard itself.** The listen after a question caught the tail of its own voice ("…रिमाइंडर भेज दो",
  which reads as haan). `isEcho` drops a long phrase made of the spoken line's words and numbers.
- **The counter is noisy.** The same listen picked up nearby talk and sent it to the model as a question.
  That listen now takes only haan or nahi. Questions go through Boliye.
- **Questions during a question dropped the task.** Now the answer is given and the open question is
  asked again, without counting toward the session's three tasks.
- **Shelf levels.** Asked for levels, the model called 2 packets "ok". Asked for counts, it counted 6 / 2
  correctly, and the app sets the level (0 empty, 1–3 low, 4+ ok). Surf came out low, so the group-order
  offer followed.
- **Guards held.** One reply leaked "khataId 2" into what Munshi would say. The answer guard now rejects
  prompt words, as well as any number not in the shop's facts.
- **Speech recognition.** The A34 has no offline Hindi pack (error 13), so the recogniser goes online.
  Error 7 (no match) after a question is normal when nobody speaks.

## Open

- Rewording tasks that were already asked wastes some GPU time; it does no harm.
- Only one pay QR is shown at a time: a newer one replaces the older.
- The shelf has been read on a synthetic image only, not a real shelf photo.
