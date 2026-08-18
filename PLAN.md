# Port plan: Crane Reader wrapper → KMP Gallery

Port of the crane-wrapper fork (Spark `~/gallery-crane`, branch `crane-wrapper`, commits
`6f76cc2` → `059797a` → `cb8ed1e`) into this KMP repo. KMP structure is the base; the wrapper
work is adapted INTO it.

## What the wrapper adds (source inventory)

| Wrapper artifact | What it is |
|---|---|
| `crane_llm_jni.c` + `libcrane_llm_jni.so` + `liblitert-lm.so` (arm64-v8a) | JNI bridge over LiteRT-LM **v0.16 C API**: per-send decoding guards (repetition penalty, no-repeat n-gram), template-safe system prompt at conversation creation, blocking streaming send + cancel. Fixes the two stock-Gallery blockers (doom loops; empty responses when an SP is set). |
| `llm/CraneLlm.kt` + `llm/LlmInferenceBridge.kt` | Kotlin side of the bridge + an Android-free interface written as a KMP seam. |
| `ui/llmchat/LlmChatModelHelper.kt` (rewritten internals) | Chat path routed through the bridge; guards + SP read from per-model config. |
| `data/Config.kt`, `ui/common/ConfigDialog.kt` | Settings: Repetition penalty (slider 1.00–1.50, def 1.15), No-repeat n-gram (slider 0–6, def 3), System prompt (new `TextInputConfig`/`TEXT_INPUT` multiline editor, prefilled with the a065 serving SP). Verified on-device incl. knob-proof (guards off → doom-loop returns). |
| `README.md` | Fork rationale, build quirks (arm64 host), JNI design. |

## Where it lands in this repo (port map)

Key discovery: this repo ALREADY has the right seam — `inference/LlmInferenceEngine.kt`
(commonMain) with `AndroidLlmInferenceEngine` (androidMain, wraps the litertlm AAR
0.9.0-alpha05) and `IosLlmInferenceEngine` (iosMain, delegates to Swift `IosLlmDelegate`).
But the Android **chat path** (`app/.../LlmChatModelHelper.kt`) bypasses that seam and calls
the AAR directly — i.e. it still has both stock bugs. The port therefore does double duty:
fix the bugs AND unify Android onto the common seam. The wrapper's own `LlmInferenceBridge`
is superseded by the repo's `LlmInferenceEngine`; only its guard/SP semantics move over.

| Concern | Destination | Nature |
|---|---|---|
| Guard + generation options | `commonMain inference/LlmInferenceEngine.kt`: new `LlmGenerationOptions(maxOutputTokens, repetitionPenalty = 1.15f, noRepeatNgramSize = 3)`, optional param on `sendMessageAsync`/`sendMessage` | small common-type extension (defaults preserve existing callers) |
| Crane C-API engine | `shared/androidMain inference/CraneLlmInferenceEngine.kt` — new `LlmInferenceEngine` impl over the JNI bridge (port of `CraneLlm`); applies `systemInstruction` via the C API at conversation creation and guards per send | clean port, adapted to seam types |
| JNI natives | `crane_llm_jni.c` at repo root (docs reference) + prebuilt `.so`s in `Android/src/app/src/main/jniLibs/arm64-v8a/` (app module packages natives; shared androidMain loads them at runtime) | copy |
| Android chat path | `app/.../LlmChatModelHelper.kt`: consume `LlmInferenceEngine` (Crane impl) instead of AAR imports; read guards/SP from model config | rework (moderate; view models untouched, same trick as the wrapper) |
| Settings | `commonMain data/Config.kt` + `ui/common/ConfigDialog.kt`: same edits as the wrapper (`TEXT_INPUT` editor type is new here too; this repo's extra `BOTTOMSHEET_SELECTOR` is untouched) | ~1:1 port — and becomes available to iOS for free |
| iOS engine | `iosMain IosLlmInferenceEngine.kt` + `IosLlmDelegate` protocol: accept `LlmGenerationOptions` and forward to the Swift delegate; Swift/MediaPipe side likely can't honor guards yet → documented no-op with TODO | stub + FLAG |
| Docs | `CRANE.md` (or README section): what/why, settings, build notes incl. arm64-host quirks, iOS verification checklist | adapt wrapper README |

Android-only, stays Android-only: the JNI `.c`/`.so`s (iOS future = cinterop over the same
LiteRT-LM C API, which ships for iOS — the seam is already right for it).

## PR breakdown (small, one concern each; never touching main directly)

1. **PR1 `crane/inference-guards`** — commonMain `LlmGenerationOptions` + seam param;
   `CraneLlmInferenceEngine` (androidMain) + JNI bridge + jniLibs; `LlmChatModelHelper`
   switched onto the seam with guards/SP from model config; iosMain accepts options
   (no-op + TODO). Android-verified on-phone.
2. **PR2 `crane/settings-ui`** — commonMain Config keys/entries + `TextInputConfig` +
   `TEXT_INPUT` editor row; defaults 1.15 / 3 / a065 SP. Android-verified (settings sheet +
   knob-proof rerun).
3. **PR3 `crane/docs`** — CRANE.md, build notes, iOS checklist.

## Verification

- Android: build APK (this box if tooling present, else the Spark exactly as before) and
  smoke-test via Spark adb (free-check first): defaults regression (phonics terminates;
  Luganda+SP non-empty) + knob-proof (1.00/0 → loop returns).
- iOS (FLAGGED for Bronson's Mac, cannot build here): compile iosMain, extend
  `IosLlmDelegate` Swift conformance for the new options param, decide MediaPipe-iOS vs
  future LiteRT-LM C-API cinterop for real guard support, run the same two smoke prompts.

## Risks / open questions

- litertlm AAR here is 0.9.0-alpha05 (wrapper was built against 0.9.0-alpha01 behavior);
  irrelevant to the C-API path but `AndroidLlmInferenceEngine` (AAR) remains for non-chat
  tasks — I will NOT delete it in PR1.
- The seam's `sendMessage`(blocking) / `sendMessageAsync` shapes differ from the wrapper's
  single blocking-streaming call; the Crane engine implements both over the same C-API
  stream (async = launched on a background dispatcher).
- Multimodal (image/audio content types in the seam): the Crane C-API path is text-only for
  now; Crane engine will reject/ignore non-text content with a log (same as the wrapper).
- `.so` size: +~39 MB in the APK (already true of the wrapper; acceptable per v1 report).
