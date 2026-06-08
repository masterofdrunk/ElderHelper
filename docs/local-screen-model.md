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

The current app code has:

- Model-file detection in `ModelAssetManager`.
- Screenshot downscaling and JPEG compression in `ScreenBitmapPreprocessor`.
- A `MiniCpmVRuntime` boundary that can be backed by the official llama.cpp JNI runtime.

The native runtime is not linked yet. The Android 15 x86_64 emulator cannot validate the official MiniCPM-V Android runtime because the upstream demo targets `arm64-v8a`.
