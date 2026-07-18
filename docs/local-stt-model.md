# Local STT Model

ElderHelper expects the local ASR model under the app private directory:

```text
/data/user/0/com.example.elderhelper/files/sherpa-onnx/asr/
  model.int8.onnx
  tokens.txt
```

For normal users, the app installs these files automatically through the first-run “下载完整离线包” flow. The ADB procedure below is a developer fallback only.

The current PoC uses the official sherpa-onnx Paraformer small Chinese + English model:

- Runtime: https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-v1.13.2-android.tar.bz2
- Model: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2
- Docs: https://k2-fsa.github.io/sherpa/onnx/android/index.html
- Model list: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html

For a debug emulator, install the APK first, then push through `/data/local/tmp`:

```powershell
$adb = "C:\Users\97611\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb push .deps\sherpa-onnx\models\sherpa-onnx-paraformer-zh-small-2024-03-09\model.int8.onnx /data/local/tmp/elderhelper-model.int8.onnx
& $adb push .deps\sherpa-onnx\models\sherpa-onnx-paraformer-zh-small-2024-03-09\tokens.txt /data/local/tmp/elderhelper-tokens.txt
& $adb shell chmod 644 /data/local/tmp/elderhelper-model.int8.onnx /data/local/tmp/elderhelper-tokens.txt
& $adb shell run-as com.example.elderhelper mkdir -p files/sherpa-onnx/asr
& $adb shell run-as com.example.elderhelper cp /data/local/tmp/elderhelper-model.int8.onnx files/sherpa-onnx/asr/model.int8.onnx
& $adb shell run-as com.example.elderhelper cp /data/local/tmp/elderhelper-tokens.txt files/sherpa-onnx/asr/tokens.txt
& $adb shell run-as com.example.elderhelper ls -l files/sherpa-onnx/asr
```

`SherpaOnnxRecognizerInstrumentedTest` is intentionally skip-friendly: it runs real native recognition when the model and test wav are present on the device, and skips when they are absent.
