# Local Screen Model

ElderHelper's M4 screen-understanding route follows the official MiniCPM-V Android demo:

- Android app source: https://github.com/OpenBMB/MiniCPM-V-Apps/tree/main/MiniCPM-V-demo-Android
- Download notes: https://github.com/OpenBMB/MiniCPM-V-Apps/blob/main/DOWNLOAD_zh.md
- Runtime family: llama.cpp on device.
- ABI: `arm64-v8a`.
- Recommended device memory: at least 6 GB.

The current project expects these files under the app private directory:

```text
/data/user/0/com.example.elderhelper/files/models/minicpm-v/
  MiniCPM-V-4_6-Q4_K_M.gguf
  mmproj-model-f16.gguf
```

The first-run model setup screen downloads both files automatically. Downloads use ModelScope first and Hugging Face as a fallback, retain partial files for HTTP Range resume, and activate a file only after size and MD5 validation.

The current app code has:

- Model-file detection in `ModelAssetManager`.
- Screenshot downscaling and JPEG compression in `ScreenBitmapPreprocessor`.
- A `MiniCpmVRuntime` boundary backed by a local `arm64-v8a` llama.cpp JNI runtime.
- A MiniCPM-V system prompt plus a short user-task prompt in `ScreenGuidancePromptBuilder`.

Native runtime notes:

- The JNI bridge is adapted from the official OpenBMB MiniCPM-V Android demo and `llama.cpp-omni`.
- For the MTMD vision path, system prompts are formatted explicitly as MiniCPM chat turns. The generic chat-template formatter aborts when called with only a system message and no user query.
- Default screenshot preprocessing caps the long edge at 640 px for acceptable phone latency.

HONOR `NTN-AN20` / Android 12 validation:

- Fixed 360x640 Chinese settings screenshot instrumentation test passes.
- Latest measured run: model load about 2.1 s, system prompt about 8.6 s, image prefill about 41.6 s, generation about 1.9 s.
- Sample output: `你先找“无线网络”这个选项。第一步：点击“无线网络”。`

The Android 15 x86_64 emulator cannot validate the official MiniCPM-V Android runtime because the upstream demo targets `arm64-v8a`.
