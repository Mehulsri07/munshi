# Voice test, <date>, on <phone model> (8 GB)

Lines: the five in `voice/lab/VoiceLab.kt` (`LAB_LINES`). Voices pushed with `android/scripts/push-voices.ps1`.

| Voice | First audio (ms) | RTF | Natural Hindi (1–5) | Reads "Sharma ji", "Surf Excel" | Reads ₹450 / ₹50,000 right | Verdict |
|---|---|---|---|---|---|---|
| Android TTS (hi-IN) | — | — | | | | |
| Piper · pratham | | | | | | |
| Piper · priyamvada | | | | | | |
| Piper · rohan | | | | | | |
| Kokoro · hf_alpha | | | | | | |
| Kokoro · hm_omega | | | | | | |

RTF = time to make the audio ÷ length of the audio. Under 1 keeps up live; above 1 only works for lines
made ahead of time.

**Emulator smoke test (2026-10-25, x86_64, not for judging quality):**
- Piper fp16 archives fail to load in sherpa-onnx 1.13.8 (float16 layers rejected), so the test uses fp32.
- Kokoro produced audio for all five lines: hf_alpha RTF 12.1, hm_omega RTF 13.3 (first line 127 s,
  including loading the model).

**Phone test (2026-10-25, Samsung Galaxy A34, SM-A346E):** the team listened to all six rows.

**Chosen:** Android TTS (hi-IN), the phone's own Google Hindi voice.
**Why:** it sounded best to the team, and it costs nothing: free, already on most Indian phones, no download
and no APK size. Per-voice timings and scores were not recorded.

**Consequence:** plan f Task 6 (bundling) was dropped. The sherpa-onnx engine, `SherpaSpeaker` and the
Awaaz jaanch screen were reverted, because they added about 24 MB of native code per ABI for a voice the app
does not use. The app speaks through `AndroidSpeaker` exactly as before plan f. The work is in git history
(commits 35589e3..f76d68a) if a bundled voice is ever needed.
