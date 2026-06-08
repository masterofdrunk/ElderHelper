# ElderHelper Local-First Development Plan

## 0. Context

ElderHelper is moving from a cloud-oriented prototype to a local-first Android assistant for older users. The PRD v1.1 requires the MVP to run locally by default:

- Local STT.
- Local screen understanding.
- Local TTS.
- No default upload of screenshots, audio, recognized text, or user questions.
- Cloud model providers are reserved for future explicit user-authorized fallback.

Current repository status:

- `MainActivity` and `AndroidManifest.xml` still reference `OverlayService`.
- `OverlayService.kt` was deleted, so the app cannot complete the assistant service flow.
- `SpeechToTextEngine` exists, but `SherpaOnnxSttEngine` is still a recorder plus placeholder.
- `ScreenCaptureService` is still a placeholder.
- Gemini dependency and `GEMINI_API_KEY` injection are still present in `app/build.gradle.kts`.
- Local build verification is blocked until `JAVA_HOME` points to a valid JDK.

## 1. Development Rules

1. Keep the app local-first by default.
2. Keep cloud calls behind explicit interfaces and user consent.
3. Do not put cloud API keys into the APK.
4. Do not log screenshots, full user questions, recognized text, or credentials in release builds.
5. Keep Android services as orchestration layers, not model or SDK dumping grounds.
6. Add dependencies only when the implementation needs them and they are documented.
7. Validate each milestone with a Gradle build and, when possible, a device smoke test.

## 2. Target Package Structure

```text
app/src/main/java/com/example/elderhelper/
  MainActivity.kt
  OverlayService.kt
  analyzer/
    AnalyzerResult.kt
    ScreenAnalyzer.kt
    LocalFirstScreenAnalyzer.kt
  model/
    ModelAssetManager.kt
  privacy/
    SensitiveOperationGuard.kt
  screen/
    ScreenCaptureProvider.kt
    MediaProjectionScreenCaptureProvider.kt
  speech/
    SpeechToTextEngine.kt
    SherpaOnnxSttEngine.kt
  tts/
    SpeechSpeaker.kt
    AndroidTtsSpeaker.kt
```

## 3. Milestones

### M0 - Restore Buildable Assistant Skeleton

Goal: recover the assistant service flow without reintroducing the old large `OverlayService`.

Tasks:

- [x] Create this `plan.md`.
- [x] Restore a minimal `OverlayService`.
- [x] Keep overlay window, foreground notification, drag handling, and click handling.
- [x] Move TTS, screen capture, and analyzer responsibilities behind interfaces.
- [ ] Confirm `MainActivity` can start the service once permissions are granted.

Acceptance:

- `OverlayService.kt` exists.
- `AndroidManifest.xml` references a real service class.
- The service does not directly depend on Gemini, Baidu ASR, or MiniCPM-V implementation details.

### M1 - Standardize Local-First Interfaces

Goal: make the AI pipeline replaceable.

Tasks:

- [x] Keep `SpeechToTextEngine` as the STT boundary.
- [x] Add `ScreenAnalyzer` and `AnalyzerResult`.
- [x] Add `SpeechSpeaker`.
- [x] Add `ScreenCaptureProvider`.
- [x] Add `SensitiveOperationGuard`.
- [x] Add `ModelAssetManager`.

Acceptance:

- The main interaction flow is `STT -> screenshot -> analyzer -> TTS`.
- Sensitive operations receive mandatory risk reminders before guidance.
- Missing local model/runtime states produce user-readable messages.

### M2 - Remove Default Cloud Coupling

Goal: align the codebase with PRD v1.1 local-first policy.

Tasks:

- [x] Remove default Gemini API key injection from `BuildConfig`.
- [x] Remove default Gemini SDK dependency.
- [x] Keep cloud provider support as a future interface only.
- [x] Keep `INTERNET` only if needed for model download or future cloud-consent builds.

Acceptance:

- Default app code path does not call Gemini or any cloud model.
- No third-party model key is required to build the app.

### M3 - Local STT PoC

Goal: complete Chinese speech recognition without Baidu SDK.

Tasks:

- [x] Add the selected sherpa-onnx Android runtime dependency.
- [x] Define ASR model directory and required files.
- [x] Implement PCM-to-text conversion in `SherpaOnnxSttEngine`.
- [x] Add error handling for missing model/runtime.

Acceptance:

- [x] Native sherpa-onnx recognizer decodes an installed local Chinese ASR model on the Android 15 emulator using an official test wav.
- [x] ElderHelper `SherpaOnnxSttEngine` decodes the same local model with emulator network disabled.
- [x] With network disabled, a Chinese spoken question can be transcribed locally through the overlay microphone flow.

External dependency:

- sherpa-onnx Android runtime and Chinese ASR model package.

Implementation notes:

- Runtime: sherpa-onnx v1.13.2 Android JNI package.
- Model used for emulator smoke: `sherpa-onnx-paraformer-zh-small-2024-03-09`.
- Model install path: `/data/user/0/com.example.elderhelper/files/sherpa-onnx/asr/`.

### M4 - MiniCPM-V Local Screen Understanding PoC

Goal: produce local screenshot-based guidance.

Tasks:

- [x] Select MiniCPM-V mobile runtime route and quantized model format.
- [ ] Implement `LocalFirstScreenAnalyzer` with Bitmap + text input.
- [x] Add screenshot compression before inference.
- [ ] Keep output short and older-user friendly.

Acceptance:

- With network disabled, at least 5 common screenshots return usable Chinese guidance.

External dependency:

- MiniCPM-V compatible Android runtime and quantized model package.

Implementation notes:

- Runtime route: OpenBMB MiniCPM-V-Apps Android demo, llama.cpp based, arm64-v8a.
- Model format: GGUF `MiniCPM-V-4_6-Q4_K_M.gguf` plus projector `mmproj-model-f16.gguf`.
- Model install path: `/data/user/0/com.example.elderhelper/files/models/minicpm-v/`.
- Current app status: screenshot preprocessing and runtime boundary are in place; native llama.cpp runtime is not linked yet.

### M5 - MVP Hardening

Goal: make the APK usable for real user testing.

Tasks:

- [ ] Add model status/settings screen.
- [ ] Add privacy explanation page.
- [ ] Add optional OCR / Accessibility context.
- [ ] Build test screenshot set for WeChat, Alipay, system settings, phone, and SMS.
- [ ] Add release logging guard.

Acceptance:

- Offline usability is above 80% in internal testing.
- Common task answer success is above 70%.
- Sensitive-operation reminder coverage is 100%.

## 4. Current Run Status

- Active milestone: M0 / M1 / M2 verification.
- Java status: `JAVA_HOME` points to `D:\AndroidStudio\jbr` (OpenJDK 21.0.10 from Android Studio).
- Gradle status: Gradle 8.13 is available from local cache and can run from `.gradle/local/gradle-8.13/bin/gradle.bat`.
- Build status: `./gradlew.bat :app:assembleDebug` passed on 2026-06-08 after enabling domestic Maven mirrors.
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`.
- Test status: `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest` passed on 2026-06-08 using AVD `ElderHelper_API35`.
- Smoke status: `MainActivity` can start `OverlayService` after recording, overlay, and MediaProjection permissions are granted on AVD `ElderHelper_API35`.
- Smoke finding: Android 15 MediaProjection defaults to "A single app"; the assistant needs users to choose "Entire screen" for screen-wide guidance.
- Smoke finding: after "Entire screen" capture starts, the `TYPE_APPLICATION_OVERLAY` button is displayed as a black square on the emulator. Investigate whether this is Android's screen-sharing overlay protection or a rendering issue before marking M0 fully done.
- Smoke finding: overlay click enters the recording path, but emulator microphone/AppOps produced `RECORD_AUDIO`/audio I/O warnings, so real microphone behavior still needs a device test.
- Fix status: `OverlayService` now declares and starts with the `microphone` foreground service type, resolving the Android 14/15 `AppOps ... op=RECORD_AUDIO` denial during overlay-triggered recording.
- Fix status: `OverlayService` no longer uses background Toasts for interaction feedback; transient messages are now rendered inside the overlay window to avoid Android background Toast suppression.
- Fix status: missing local STT / screen model placeholder messages are now short Chinese user-facing guidance and no longer expose internal model paths in the overlay.
- Next confirmation point: link the MiniCPM-V llama.cpp Android native runtime on an arm64 test device, then run 5 screenshot guidance cases offline.
