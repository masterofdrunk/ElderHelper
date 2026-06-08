# ElderHelper Architecture

## Product Direction

ElderHelper is local-first by default. The app should help older users understand phone screens through voice input, screen capture, local analysis, and local TTS. Screenshots, audio, recognized text, and user questions must not be uploaded in the default MVP flow.

## Runtime Flow

```text
MainActivity
  -> permission checks
  -> OverlayService
       -> SpeechToTextEngine
       -> ScreenCaptureProvider
       -> ScreenAnalyzer
       -> SpeechSpeaker
```

## Package Boundaries

```text
analyzer/  Screen understanding interface and implementations.
model/     Local model paths, version checks, and future download state.
privacy/   Sensitive operation detection and privacy rules.
screen/    MediaProjection screenshot boundary.
speech/    Local STT boundary and implementations.
tts/       Local TTS boundary and Android system TTS implementation.
```

## Implementation Rules

1. `OverlayService` coordinates the assistant flow only.
2. Cloud providers must not be called from the default code path.
3. Cloud API keys must not be injected into `BuildConfig`.
4. Local model runtime failures must return user-readable guidance.
5. Sensitive operations such as payment, transfer, verification code, login, password, and authorization must include a risk reminder.
6. Release builds must not log full user questions, screenshots, credentials, or model request payloads.

## External Runtime Decisions

The following implementation choices are now selected for the current PoCs:

- STT: sherpa-onnx Android JNI runtime with `sherpa-onnx-paraformer-zh-small-2024-03-09`.
- Screen understanding: OpenBMB MiniCPM-V-Apps Android route, llama.cpp native runtime, GGUF model files.

The following implementation choices are still pending:

- Optional OCR / Accessibility context provider.
- Future cloud fallback provider and consent flow.
