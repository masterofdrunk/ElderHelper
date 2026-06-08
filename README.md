# ElderHelper

ElderHelper 是一个面向老年用户的 Android 本地优先手机助手。用户通过悬浮按钮语音提问，应用在本机完成语音识别、截屏、看屏分析和语音播报，目标是在不默认上传截图、录音、识别文本或问题内容的前提下，帮助用户理解当前手机界面并完成常见操作。

## 当前状态

项目正在从早期云端原型迁移到本地优先 MVP。当前默认路径不再依赖 Gemini 或百度语音 SDK：

```text
悬浮按钮
  -> 本地语音识别 sherpa-onnx
  -> MediaProjection 截屏
  -> 本地看屏分析接口 MiniCPM-V 路线
  -> Android 系统 TTS 播报
```

## 已完成

- 恢复并简化 `OverlayService`，保留悬浮窗、拖动、前台服务通知和点击交互。
- 抽象出本地优先接口：
  - `SpeechToTextEngine`
  - `ScreenCaptureProvider`
  - `ScreenAnalyzer`
  - `SpeechSpeaker`
  - `ModelAssetManager`
  - `SensitiveOperationGuard`
- 移除默认 Gemini SDK 和 API key 注入。
- 移除默认百度语音 native 库。
- 修复 Android 14/15 前台服务麦克风类型问题，解决 `RECORD_AUDIO` AppOps 拒绝。
- 移除后台 Toast 反馈，改为悬浮窗内部状态提示，避免系统压制后台 Toast。
- 接入 sherpa-onnx Android JNI runtime。
- 实现本地中文 STT：
  - 录音 PCM 采集
  - PCM16LE 转 float samples
  - 本地 Paraformer 模型解码
  - 缺模型、缺运行库、空语音的中文提示
- 选定 M4 看屏路线：
  - OpenBMB MiniCPM-V-Apps Android demo
  - llama.cpp 端侧运行
  - GGUF 模型格式
  - `arm64-v8a` 真机验证
- 增加截图压缩预处理和 `MiniCpmVRuntime` 边界，为 MiniCPM-V native 接入做准备。

## 已验证

本地环境：

- Android Studio JBR / OpenJDK 21
- Android SDK API 35
- AVD: `ElderHelper_API35`

已通过：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

本地 STT 已验证：

- sherpa-onnx native recognizer 可在 Android 15 模拟器解码官方中文/英文测试 wav。
- `SherpaOnnxSttEngine` 可通过同一套本地模型完成解码。
- 关闭模拟器网络后，本地 STT instrumentation 测试仍通过。
- 悬浮窗麦克风流程已走到“语音识别成功 -> 看屏模型未安装”分支，说明 STT 端到端路径已打通。

## 本地模型

### ASR 模型

当前 STT PoC 使用 sherpa-onnx 官方中文/英文小模型：

- Runtime: `sherpa-onnx-v1.13.2-android`
- Model: `sherpa-onnx-paraformer-zh-small-2024-03-09`

App 私有目录：

```text
/data/user/0/com.example.elderhelper/files/sherpa-onnx/asr/
  model.int8.onnx
  tokens.txt
```

详细安装步骤见 [docs/local-stt-model.md](docs/local-stt-model.md)。

### 看屏模型

当前 M4 路线参考 OpenBMB 官方 Android demo：

- Runtime: llama.cpp on device
- ABI: `arm64-v8a`
- 推荐内存：至少 6 GB
- Model: `MiniCPM-V-4_6-Q4_K_M.gguf`
- Projector: `mmproj-model-f16.gguf`

App 私有目录：

```text
/data/user/0/com.example.elderhelper/files/models/minicpm-v/
  MiniCPM-V-4_6-Q4_K_M.gguf
  mmproj-model-f16.gguf
```

详细说明见 [docs/local-screen-model.md](docs/local-screen-model.md)。

## 权限

当前 MVP 需要：

- `RECORD_AUDIO`：语音提问。
- `SYSTEM_ALERT_WINDOW`：显示悬浮按钮。
- `FOREGROUND_SERVICE`：维持助手服务。
- `FOREGROUND_SERVICE_MICROPHONE`：Android 14/15 麦克风前台服务类型。
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`：截屏前台服务类型。
- `FOREGROUND_SERVICE_SPECIAL_USE`：悬浮助手服务声明。
- `INTERNET`：当前默认路径不上传数据，后续可用于模型下载或用户显式授权的云端 fallback。

## 如何构建

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = "D:\AndroidStudio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 当前限制

- MiniCPM-V native runtime 尚未接入当前 app，只完成了模型检测、截图压缩和运行时边界。
- 官方 MiniCPM-V Android demo 目标 ABI 是 `arm64-v8a`，当前 x86_64 AVD 不能真实验证看屏模型。
- Android 15 MediaProjection 默认可能选择 “A single app”，实际全屏辅助需要用户选择 “Entire screen”。
- 模拟器上开启全屏录屏后，悬浮按钮有时会显示为黑色方块，需要继续确认是系统录屏保护还是渲染问题。

## 下一步

- 接入 OpenBMB MiniCPM-V Android demo 的 llama.cpp native runtime。
- 在 `arm64-v8a` 真机上安装 MiniCPM-V GGUF 模型并跑通本地看屏。
- 用微信、支付宝、系统设置、电话、短信等 5 类截图做离线指导测试。
- 增加模型状态/设置页，提示用户安装 STT 和看屏模型。
- 增加隐私说明页和 release 日志保护。

## 参考

- sherpa-onnx Android: https://k2-fsa.github.io/sherpa/onnx/android/index.html
- sherpa-onnx Paraformer models: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html
- MiniCPM-V-Apps Android: https://github.com/OpenBMB/MiniCPM-V-Apps/tree/main/MiniCPM-V-demo-Android
- MiniCPM-V-Apps download notes: https://github.com/OpenBMB/MiniCPM-V-Apps/blob/main/DOWNLOAD_zh.md
