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

### M3.5 - In-App Offline Model Delivery

Goal: keep the APK small while allowing ordinary users to prepare every offline capability from inside the app without manually copying model files.

Tasks:

- [x] Add a model setup screen shown before the assistant permission flow.
- [x] Add a versioned model catalogue for ASR, MiniCPM-V language, and MiniCPM-V projector files.
- [x] Download all required files with one user action.
- [x] Support progress reporting, cancellation, retry, and HTTP range resume.
- [x] Verify expected file size and checksum before activation.
- [x] Download into a staging file and atomically rename only after verification.
- [x] Check Wi-Fi/network state and available storage before large downloads.
- [x] Keep model files independent from APK upgrades so app updates do not redownload unchanged models.
- [x] Provide short Chinese status and recovery messages suitable for older users.

Acceptance:

- [x] The base APK does not contain ASR or MiniCPM-V model weights.
- [x] A fresh install offers one clear “下载完整离线包” action.
- [x] Interrupted downloads continue from the existing partial file.
- [x] The assistant cannot start until required ASR and screen model files pass validation.
- [ ] After model setup completes, voice recognition and screen analysis can run with networking disabled. (Needs full overlay E2E and network-off validation.)

Implementation notes:

- Default complete pack: sherpa-onnx Paraformer ASR plus MiniCPM-V 4.6 Q4_K_M and f16 projector.
- Expected complete model download is about 1.7 GB, excluding any future bundled TTS model.
- Store active models under the existing app-private paths and partial files under a staging directory.
- Model URLs and checksums must come from a versioned catalogue and official or project-controlled distribution endpoints.
- Android system TTS remains the default for this milestone; a self-contained local TTS pack is a separate future milestone.

### M4 - MiniCPM-V Local Screen Understanding PoC

Goal: produce local screenshot-based guidance.

Tasks:

- [x] Select MiniCPM-V mobile runtime route and quantized model format.
- [x] Implement `LocalFirstScreenAnalyzer` with Bitmap + text input.
- [x] Add screenshot compression before inference.
- [x] Keep output short and older-user friendly.
- [x] Add a MiniCPM-V system prompt and short task prompt for screen-evidence-first guidance.
- [x] Add a deterministic intent and screen-text preparation layer before MiniCPM-V inference.
- [x] Add a local Agent router: use explicit screen text or safety rules first, and only capture a screenshot / invoke MiniCPM-V when evidence is insufficient.
- [x] Bundle an offline lexical RAG knowledge base for common, low-risk mobile tasks before attempting screen-model inference.

Acceptance:

- With network disabled, at least 5 common screenshots return usable Chinese guidance.

External dependency:

- MiniCPM-V compatible Android runtime and quantized model package.

Implementation notes:

- Runtime route: OpenBMB MiniCPM-V-Apps Android demo, llama.cpp based, arm64-v8a.
- Model format: GGUF `MiniCPM-V-4_6-Q4_K_M.gguf` plus projector `mmproj-model-f16.gguf`.
- Model install path: `/data/user/0/com.example.elderhelper/files/models/minicpm-v/`.
- Current app status: native llama.cpp runtime is linked for `arm64-v8a`; one fixed Chinese settings screenshot passes on HONOR `NTN-AN20` / Android 12.
- Latest fixed screenshot output: `你先找“无线网络”这个选项。第一步：点击“无线网络”。`
- Prompt preparation now classifies navigation, page identification, troubleshooting, sensitive operations, and general help. It can use future OCR / Accessibility text after long numeric values are hidden.
- An optional Accessibility text provider now supplies short-lived, local-only visible screen text. When it finds an explicit target such as “无线网络”, the Agent answers without screenshot capture or MiniCPM-V inference.
- The Agent now has a bundled local RAG knowledge base for system settings, calls/SMS, WeChat, camera/photos, and urgent help. Each entry has common question wording, optional visible screen labels, and separate answers for the current page versus the route from elsewhere. It only accepts an entry with a direct question-keyword match; uncertain questions still fall through to visual inference.

### M5 - Product Agent Core (replace the Demo approach)

Goal: make ElderHelper a complete, recoverable assistant for a defined set of everyday phone tasks, not a collection of model demonstrations.

Product loop:

```text
老人说需求 → 本地意图判断 → 已验证的任务流程 / RAG → 读取当前页面文字
       → 需要视觉证据时才调用 MiniCPM-V → 语音播报下一步 → 询问是否完成 / 继续 / 退出
```

Tasks:

- [x] Add an Agent session state machine: idle, listening, planning, guiding, waiting for confirmation, retrying, and finished.
- [x] Add a capability registry with a stable identifier, entry conditions, step templates, recovery wording, and risk level for each supported task.
- [x] Add an App playbook catalogue. Each playbook records Android package names, supported task flows, screen-label variants, safe exit/recovery actions, last-verified app version, and whether the flow is guidance-only or blocked as high risk.
- [x] Turn the offline RAG data into twelve complete capability packs, each with synonym coverage, page-label variants, recovery wording, and a stated risk level (86 named tasks total):
  - [x] Phone and contacts: answer/make calls, contacts, SMS, missed calls, voicemail, blocking nuisance calls, and emergency calls.
  - [x] Family and social connection: WeChat text/voice/photo/video, group messages, video calls, sharing location, and family-member handoff.
  - [x] Health and care: hospital/clinic appointment entry guidance, health-code or medical-insurance entry guidance, reports, medication reminders, wearable/device data, and family-doctor contact. Medical judgement remains out of scope.
  - [x] Travel and mobility: maps/navigation, ride-hailing handoff, public-transit and ticket entry guidance, trip sharing, and local emergency contacts.
  - [x] Shopping and daily services: grocery/delivery entry guidance, utility-service entry guidance, order tracking, refunds/after-sales entry, and community services.
  - [x] Finance and scam prevention: payment/transfer/banking entry guidance, receipt lookup, fraud warnings, and trusted-family handoff. The app must never confirm a payment or request/repeat credentials.
  - [x] Government and civic services: government-service apps, social-security/medical-insurance entry guidance, document/photo preparation, and local-service hotline handoff.
  - [x] News, entertainment, and learning: news reading, audio/video playback, audiobooks, photos, simple games, classes, and subscription/advertisement escape paths.
  - [x] Camera, photos, and documents: take/find/share photos, screenshots, scan a document, QR-code entry guidance, and print/share handoff.
  - [x] Device and accessibility: Wi-Fi, mobile data, Bluetooth, volume, brightness, font size, voice input, flashlight, battery, storage, updates, permissions, and accessibility settings.
  - [x] Home and community, first slice: community notices plus meal, housekeeping, and repair-service entry guidance. Device control remains opt-in and requires a supported integration.
  - [x] Safety and resilience: emergency calling, lost-phone recovery entry, account-security recovery entry, scam interruption, privacy settings, task cancellation, and “ask a family member” escalation.
- [ ] Build the first App playbook release in tiers rather than treating all apps as generic screens:
  - [x] Daily essentials, first slice: system Settings, Phone, Messages, Camera, and Photos/Album.
  - [x] Social and family, first slice: WeChat photo, voice message, and voice/video-call guidance.
  - [x] Travel and public life, first slice: Amap and Baidu Maps navigation entry guidance.
  - [x] Shopping and services, first slice: Alipay service entry, Taobao, JD, Pinduoduo, Meituan, and Ele.me search/order guidance with an explicit stop-before-payment boundary.
  - [x] Healthcare and civic services, first slice: Alipay medical-health, health-insurance, social-security, and local-government-service entry guidance; authentication, confirmation, and payment remain user-only.
  - [x] Entertainment and learning, first slice: WeChat Channels, Douyin, and Kuaishou video search plus safe advertisement-exit guidance.
- [x] Capture the foreground package and visible text through the optional Accessibility service, then select the relevant App playbook before generic RAG or MiniCPM-V fallback.
- [x] Finish the optional Accessibility text source and show clear enabled/disabled status in the setup screen.
- [x] Add a bounded fallback policy: use MiniCPM-V only when no capability has enough evidence; explain a retry when screenshot/text evidence is inadequate.
- [x] Add a visible interaction transcript with a large replay button, so the user can hear the current instruction again without repeating the whole question.
- [x] Add task progress confirmation: after each guided step, accept “好了 / 没找到 / 返回” and either continue, recover, or stop.
- [x] Extend model setup with update, delete, and storage-space explanation; privacy explanation is now available from the setup screen.
- [x] Add release logging guard: never log screenshots, recognized text, accessibility text, or credential-like strings in release builds.

Acceptance:

- A new user can install the app, understand every permission, download models, and start the assistant without developer help.
- At least 60 named high-frequency tasks across all twelve capability packs have an end-to-end local guidance path, including recovery wording and a safe fallback.
- The first App playbook release supports at least 15 named apps or system apps and validates every supported flow against a recorded, consented screen set. App/version mismatch must fall back safely instead of guessing.
- At least 80% of the curated task set completes without calling MiniCPM-V when Accessibility text is enabled.
- For tasks that require visual inference, the assistant clearly says when it is looking at the screen and returns usable Chinese guidance offline.
- Sensitive-operation reminder coverage is 100%; the app never asks for or repeats passwords, verification codes, card numbers, or ID numbers.

### M6 - Real-device Validation and Release Readiness

Goal: prove the product loop on real phones before calling the app usable.

Tasks:

- [ ] Build a consented test set for settings, WeChat, phone, SMS, camera, photos, maps, and safety cases across at least two Android variants.
- [ ] Run network-off end-to-end tests for every capability pack.
- [ ] Measure response time separately for local rule/RAG answers and MiniCPM-V fallback answers.
- [ ] Have older adult testers complete the highest-frequency tasks; record only opt-in, anonymized task success results.
- [ ] Fix all flows with unclear wording, unsafe suggestions, or dead-end recovery.

Acceptance:

- Offline usability is above 80% in supervised internal testing.
- Common-task completion is above 70% for target users.
- No critical privacy or unsafe-operation failures in the release candidate.

### M7 - Consent-based Workflow Learning Harness

Goal: let users teach low-risk, repeated phone operations once and reuse only verified, privacy-safe guidance workflows.

Rules:

- Learning is always explicitly started and stopped by the user; it is off by default.
- Store semantic action structure only, never raw screenshots, audio, chat content, contact names, passwords, verification codes, card/ID numbers, or editable-field content.
- Immediately stop and discard a learning session in a high-risk App or on a high-risk control; payment, transfer, login, identity, medical-record, and government-authentication flows are never learned or replayed.
- A learned workflow is guidance-only. It may tell the user the next verified step, but never performs taps or confirms an irreversible action.
- A draft becomes reusable only after a user or family-member review and explicit save. Version/package/label mismatch invalidates the workflow and falls back safely.

Tasks:

- [x] Add an explicit “教我一次” session controller with in-memory, local-only semantic action capture.
- [x] Add a review/approve/discard step and a local store for approved low-risk workflow drafts.
- [x] Record only app package, stable view identifier, allow-listed UI labels, order, and success/stop status; block sensitive input and high-risk apps.
- [x] Add learned-workflow retrieval before generic RAG and visual-model fallback.
- [x] Require trusted-family or user confirmation before promoting a draft to the local learned-workflow store.
- [x] Add a 30-day workflow expiry and strict app-version/package mismatch invalidation.
- [x] Add a privacy page explaining learning mode and a one-tap delete-all-learned-workflows control.

Acceptance:

- A user can teach and approve a low-risk task such as “在微信聊天页发送照片”; the app stores no chat contents, contact identity, screenshot, or raw audio.
- On the same App and compatible UI, the learned workflow can be replayed as spoken guidance without invoking MiniCPM-V.
- All high-risk learning attempts are visibly blocked and leave no draft or persisted trace.

## 4. Current Run Status

- Active milestone: M4 MiniCPM-V native runtime integration.
- Java status: `JAVA_HOME` points to `D:\AndroidStudio\jbr` (OpenJDK 21.0.10 from Android Studio).
- Gradle status: the Gradle 8.13 Wrapper distribution is installed in the user Gradle cache.
- Build status: `./gradlew.bat :app:assembleDebug` passed on 2026-06-29.
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`.
- Test status: `:app:testDebugUnitTest` passed with catalogue, checksum, and controlled HTTP Range resume coverage; `:app:connectedDebugAndroidTest` passed 3/3 on HONOR NTN-AN20 (Android 12, arm64-v8a).
- Smoke status: `MainActivity` can start `OverlayService` after recording, overlay, and MediaProjection permissions are granted on AVD `ElderHelper_API35`.
- Smoke finding: Android 15 MediaProjection defaults to "A single app"; the assistant needs users to choose "Entire screen" for screen-wide guidance.
- Smoke finding: after "Entire screen" capture starts, the `TYPE_APPLICATION_OVERLAY` button is displayed as a black square on the emulator. Investigate whether this is Android's screen-sharing overlay protection or a rendering issue before marking M0 fully done.
- Smoke status: the HONOR NTN-AN20 real microphone flow recorded and locally recognized a 19-character utterance through the overlay service.
- Fix status: `OverlayService` now declares and starts with the `microphone` foreground service type, resolving the Android 14/15 `AppOps ... op=RECORD_AUDIO` denial during overlay-triggered recording.
- Fix status: `OverlayService` no longer uses background Toasts for interaction feedback; transient messages are now rendered inside the overlay window to avoid Android background Toast suppression.
- Fix status: missing local STT / screen model placeholder messages are now short Chinese user-facing guidance and no longer expose internal model paths in the overlay.
- Delivery decision: ship a small APK and let users download the complete offline model pack through the app after installation.
- M3.5 delivery status: implementation and fresh-install validation are complete.
- M3.5 validation status: the connected HONOR NTN-AN20 downloaded and checksum-validated both MiniCPM-V files from ModelScope, installed the sherpa archive through the in-app extractor, and unlocked the assistant permission flow.
- Next confirmation point: link the MiniCPM-V llama.cpp Android native runtime, then run 5 screenshot guidance cases offline.
