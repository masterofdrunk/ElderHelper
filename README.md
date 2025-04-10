这是一个利用ai帮助中老年人使用手机的app
由于苹果的隐私限制所以暂时只有Android版本
且demo版本ui不好看，功能不稳定
目前多模态大模型使用的是gemini，对国内用户不是很友好，但是gemini送了500刀😄
# Elder Helper (老年人助手)

## 简要描述

Elder Helper 是一款旨在帮助老年人更方便地使用智能手机的 Android 应用。它提供一个悬浮按钮，用户可以通过语音提问，并结合当前屏幕内容，获取 AI 的帮助和指导，让复杂的操作变得简单。

## 主要功能

*   **悬浮助手按钮**: 一个可拖动的悬浮按钮，常驻屏幕边缘，方便随时调用。
*   **语音交互**: 点击按钮后，可通过中文语音进行提问或发出指令。
*   **屏幕理解**: 在语音提问的同时，应用会截取当前屏幕内容。
*   **多模态 AI 分析**: 将语音识别的文本和屏幕截图发送给多模态大模型 (当前配置为 Google Gemini 1.5 Flash) 进行分析。
*   **语音播报 (TTS)**: 将 AI 模型的回复通过语音清晰地播报给用户。
*   **权限引导**: 应用启动时会引导用户授予必要的权限。

## 环境要求

*   Android Studio (建议使用最新稳定版)
*   Android SDK (根据项目 `build.gradle.kts` 配置，例如 `minSdk = 24`, `compileSdk = 35`)
*   一台 Android 设备或模拟器 (建议 Android 7.0 Nougat 或更高版本)
*   稳定的网络连接 (用于 AI API 调用)

## 安装与设置

1.  **克隆仓库**:
    ```bash
    git clone <your-repository-url> # 替换为您的仓库 URL
    cd elderhelper
    ```

2.  **获取 API 密钥**:
    *   您需要一个 Google Gemini API 密钥才能使用 AI 功能。
    *   前往 [Google AI Studio](https://aistudio.google.com/app/apikey) 获取您的密钥。

3.  **配置 API 密钥**:
    *   在项目的**根目录**下 (与 `app` 文件夹同级)，创建一个名为 `local.properties` 的文件（如果它尚不存在）。
    *   在该文件中添加以下一行，并将 `<YOUR_GEMINI_API_KEY>` 替换为您在步骤 2 中获取的真实密钥:
        ```properties
        GEMINI_API_KEY=<YOUR_GEMINI_API_KEY>
        ```
    *   **安全提示**: 确保 `local.properties` 文件已被添加到项目的 `.gitignore` 文件中，以防止意外将您的密钥上传到代码仓库。`.gitignore` 文件中应包含 `/local.properties` 这一行。

4.  **构建项目**:
    *   使用 Android Studio 打开项目。
    *   等待 Android Studio 完成 Gradle 同步（可能需要下载依赖项）。如果遇到问题，可以尝试菜单中的 **File > Sync Project with Gradle Files**。
    *   构建项目 (**Build > Make Project** 或 **Build > Rebuild Project**)。

## 使用说明

1.  **启动应用**: 在 Android Studio 中运行应用到连接的设备或模拟器上。
2.  **授予权限**:
    *   应用首次启动时会进入一个权限检查界面。
    *   点击界面上的按钮 (通常显示为 "检查并请求权限" 或类似文本)。
    *   系统会弹出请求权限的对话框。请务必授予以下所有权限：
        *   **录音权限 (Record Audio)**：用于语音输入。
        *   **悬浮窗权限 (Display over other apps / System Alert Window)**：用于显示悬浮按钮。这可能需要跳转到系统设置页面手动开启。
        *   **屏幕捕获权限 (Screen Capture)**：用于理解屏幕内容。这通常会在您第一次尝试启动服务时弹出一次性确认框。
    *   请按照提示完成所有权限的授予。按钮文本会更新以反映当前的权限状态。
3.  **启动服务**: 当所有必要权限都授予后，主界面上的按钮文本会变为类似 "启动助手服务"。点击此按钮。
4.  **使用悬浮按钮**:
    *   屏幕上会出现一个助手悬浮按钮。
    *   **长按并拖动**按钮可以将其移动到屏幕上您觉得方便的位置。
    *   **单击**悬浮按钮。
    *   您会听到或看到提示 "请说话..."。
    *   此时，请用**中文**清晰地说出您的问题或指令 (例如：“微信怎么加好友？”、“这个付款按钮在哪里？”)。
    *   说完后，请**等待一小会儿**，不要再次点击按钮。应用会自动检测语音结束。
    *   应用会将您的语音问题（转换为文本）和当前的屏幕截图发送给 AI 进行分析。
    *   耐心等待片刻，AI 的回复将通过语音播报出来。
    *   一次交互完成后，您可以再次单击按钮开始新的提问。

## 所需权限

应用正常运行需要以下 Android 权限：

*   `android.permission.RECORD_AUDIO`: 用于语音识别。
*   `android.permission.SYSTEM_ALERT_WINDOW`: 用于显示悬浮窗。
*   `android.permission.FOREGROUND_SERVICE`: 用于让服务在后台稳定运行。
*   `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` (Android 10+): 明确前台服务类型为屏幕捕获。
*   `android.permission.INTERNET`: 用于调用 AI API。

## 未来可扩展方向 (TODO)

*   [ ] 优化对 AI 的提示 (Prompt Engineering)，以获得更准确、更符合老年人习惯的回复。
*   [ ] 探索支持更自然的连续对话能力。
*   [ ] 增加设置选项，允许用户自定义悬浮窗外观、TTS 语速、音量等。
*   [ ] 考虑加入视觉指示，例如在 AI 回复中高亮屏幕上的相关区域。
*   [ ] 评估和适配其他优秀的（国内）多模态大模型 API。
*   [ ] 添加更完善的错误处理和用户反馈机制。

---

## 近期更新说明 (2025-04-10) 

本次更新主要围绕集成百度语音识别 SDK 并解决相关问题展开，同时优化了交互流程：

*   **集成百度语音识别 (ASR)**:
    *   引入了百度语音识别 SDK 以支持中文语音输入。
    *   增加了对百度 `APP_ID`, `API_KEY`, `SECRET_KEY` 的配置需求（通过 `local.properties`）。
    *   添加了放置百度 SDK 相关文件（`.jar`, `.so`, `assets` 资源）的说明。
*   **交互流程变更**:
    *   语音输入的交互方式改为：**单击按钮开始录音，再次单击按钮结束录音**。暂时移除了基于 VAD 的自动语音结束检测，以解决 VAD 组件初始化问题。
*   **问题修复**:
    *   解决了百度 SDK 的 `-3004` 鉴权失败错误，强调了在百度云控制台核对密钥和绑定包名的重要性。
    *   解决了 VAD 初始化失败 (`VAD is not available`) 的问题。
    *   通过引入状态管理 (`isEngineReadyForNext`) 修复了连续快速点击时可能出现的 "ASR Engine is busy" 错误。
    *   修复了因缺少 `ACCESS_NETWORK_STATE` 权限导致的崩溃。
    *   解决了集成过程中遇到的各种编译和构建错误（如 `BuildConfig` 无法解析、`R.jar` 文件锁定等）。

核心的 AI 分析功能仍由 Google Gemini 实现，结合了语音识别结果和屏幕截图信息。

---