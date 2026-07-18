# ElderHelper

ElderHelper 是一个面向老年用户的 Android 本地优先手机助手。用户可以通过悬浮按钮说出问题，应用在手机本机完成语音识别、页面理解、步骤规划与语音播报，帮助用户找到当前页面中的按钮，并安全地完成常见手机操作。

项目仍处于开发与真机验证阶段，不建议用于无人陪同的医疗、金融或紧急决策。

## 核心能力

- 本地语音识别：使用 sherpa-onnx Paraformer，在设备端将中文语音转为文字。
- 本地任务路由：优先使用安全规则、App playbook、已批准流程和离线 RAG，证据不足时才调用视觉模型。
- 离线看屏：通过 MiniCPM-V 4.6、llama.cpp-omni 和 JNI 在 arm64 Android 设备上分析当前截图。
- 可选快速看屏：无障碍服务只读取当前前台 App 名称和可见文字，用于减少视觉模型调用。
- 逐步指导：每次只播报短步骤，支持“好了”“没找到”“返回”等恢复与退出指令。
- 本地流程学习：用户主动发起、审核并保存低风险操作结构；流程过期或 App 不匹配时自动失效。
- 离线模型管理：应用内下载、断点续传、校验、更新和删除完整模型包。
- 隐私与安全：默认不上传语音、截图、问题或页面文字，敏感页面只提供边界明确的指导。

## 12 个离线能力包

内置词法 RAG 目前包含 12 个能力包、86 个具名任务。每个任务都有同义问法、页面标签变体、恢复话术和风险等级。

| 能力包 | 覆盖内容 |
| --- | --- |
| 电话与通讯录 | 接听与拨号、联系人、短信、未接来电、语音信箱、骚扰拦截、紧急呼叫 |
| 微信与社交 | 文字、语音、照片、群聊、音视频通话、位置分享、家人协助 |
| 医疗健康 | 挂号入口、医保服务、报告查询、用药提醒、穿戴数据、家庭医生 |
| 出行 | 地图导航、公交地铁、叫车、票务、行程分享、紧急联系 |
| 购物服务 | 买菜外卖、商品搜索、生活缴费、物流、退款售后、社区服务 |
| 金融防诈 | 支付与转账入口、手机银行、回单查询、诈骗中断、可信家人核对 |
| 政务 | 政务入口、社保医保、材料准备、12345 等官方热线 |
| 娱乐学习 | 新闻、音视频、听书、照片回忆、小游戏、课程、广告与订阅退出 |
| 相机照片文档 | 拍照、相册、截图、分享、扫描、二维码、打印交接 |
| 设备与无障碍 | Wi-Fi、流量、蓝牙、音量、亮度、字体、语音输入、存储、更新与权限 |
| 居家与社区 | 社区通知、助餐、家政、维修服务 |
| 安全与恢复 | 紧急求助、丢失手机、账号恢复、诈骗中断、隐私设置、任务取消 |

知识数据位于：

- `app/src/main/assets/local_help_capability_packs.json`
- `app/src/main/assets/local_help_knowledge.json`

## 工作流程

```text
悬浮按钮
  -> sherpa-onnx 本地语音识别
  -> 敏感操作与紧急情况规则
  -> 已批准的本地流程
  -> 前台 App playbook
  -> 12 包离线 RAG + 可见页面文字
  -> 证据不足时才截屏并调用 MiniCPM-V
  -> Android 系统 TTS 播报下一步
  -> 等待完成、恢复或退出
```

当前 App playbook 识别系统设置、电话、短信、相机、相册、微信、支付宝、高德地图、百度地图、滴滴、铁路 12306、淘宝、京东、拼多多、美团、饿了么、抖音和快手。涉及付款、下单、身份认证或授权的 App 只提供入口与核对提醒。

## 安全边界

ElderHelper 是指导助手，不是自动操作工具。

- 不代替用户点击付款、转账、登录、授权、下单或身份认证。
- 不索要、保存、复述密码、验证码、银行卡号或身份证号。
- 不提供医疗诊断、用药剂量判断或治疗建议。
- 遇到紧急危险时优先提示直接拨打 120、110 或当地紧急电话。
- 学习模式只保存经审核的通用动作结构，不保存截图、录音、聊天内容、联系人姓名或输入内容。
- 发布构建禁止记录截图、识别文字、无障碍文字和凭证类内容。

完整隐私说明可在应用内“隐私”页面查看。

## 离线模型

模型权重不包含在 Git 仓库或基础 APK 中。首次启动后，用户可在“离线”页面下载约 1.7 GB 的完整离线包。

| 用途 | 模型/运行时 | 下载大小 |
| --- | --- | ---: |
| 中文语音识别 | sherpa-onnx Paraformer small | 约 78 MB |
| 看屏语言模型 | MiniCPM-V 4.6 Q4_K_M GGUF | 约 529 MB |
| 视觉投影模型 | MiniCPM-V 4.6 mmproj f16 | 约 1.1 GB |

下载流程支持 HTTP Range 断点续传、网络与存储检查、文件大小和校验值验证、暂存文件原子激活。MiniCPM-V 默认使用 ModelScope，Hugging Face 作为备用源；ASR 模型来自 sherpa-onnx 官方发布页。

设备要求：

- Android 7.0 或更高版本（API 24+）。
- 当前原生看屏运行时仅支持 `arm64-v8a`。
- 建议至少 6 GB 内存和约 2 GB 可用存储空间。
- MiniCPM-V 的速度取决于设备 CPU 与内存带宽。

模型路径和开发者手动安装方式见：

- [本地语音模型说明](docs/local-stt-model.md)
- [本地看屏模型说明](docs/local-screen-model.md)

## 权限说明

| 权限/能力 | 用途 | 是否可选 |
| --- | --- | --- |
| 麦克风 | 接收语音问题 | 使用语音助手时必需 |
| 悬浮窗 | 在其他 App 上显示助手按钮和指导 | 使用悬浮助手时必需 |
| 屏幕捕获 | 在需要视觉证据时读取当前屏幕 | 每次由用户确认 |
| 无障碍服务 | 读取当前 App 名称和可见文字，加快找按钮 | 可选 |
| 通知与前台服务 | 在录音、看屏和模型下载期间保持任务可见 | 按 Android 版本需要 |
| 网络 | 首次下载或更新离线模型 | 模型安装后日常指导不需要 |

## 构建项目

### 环境

- Android Studio 自带 JDK 21
- Android SDK 35
- Android NDK `27.0.12077973`
- CMake `3.22.1`
- Git submodule 支持

### 克隆

```powershell
git clone --recurse-submodules https://github.com/masterofdrunk/ElderHelper.git
Set-Location ElderHelper
```

已有仓库需要初始化原生依赖：

```powershell
git submodule update --init --recursive
```

`third_party/llama.cpp-omni` 固定到项目验证过的提交。不要直接替换为任意上游版本，否则 MiniCPM-V JNI 接口可能不兼容。

### 编译

确保 `local.properties` 指向 Android SDK，然后执行：

```powershell
$env:JAVA_HOME = "D:\AndroidStudio\jbr"
.\gradlew.bat :app:assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试

```powershell
$env:JAVA_HOME = "D:\AndroidStudio\jbr"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

当前已验证：

- JVM 单元测试通过。
- Debug APK 和 Android 测试 APK 构建通过。
- 12 个能力包、86 个任务的数据完整性检查通过。
- HONOR NTN-AN20（Android 12，arm64-v8a）离线 RAG 仪器测试 3/3 通过。
- 同一真机可加载 MiniCPM-V 4.6 GGUF 与 projector，并对 360 x 640 中文设置页截图返回本地中文指导。
- 固定看屏样例中，模型加载约 2.1 秒，系统提示约 8.6 秒，图像预填充约 41.6 秒，生成约 1.9 秒；该数字只代表当前测试设备和样例。

## 项目结构

```text
app/src/main/
  assets/          12 个离线能力包与 RAG 知识
  cpp/             MiniCPM-V / llama.cpp JNI 桥接
  java/.../
    accessibility/ 可选页面文字来源
    agent/         路由、playbook、会话恢复与学习流程
    analyzer/      RAG、提示构建、截图预处理与视觉运行时
    model/         模型目录、下载、校验与生命周期管理
    privacy/       敏感操作保护
  res/             适老界面、隐私页与无障碍配置
docs/              模型与产品设计文档
third_party/       固定版本的原生运行时子模块
```

## 当前限制

- MiniCPM-V 看屏目前仅支持 arm64-v8a，x86_64 模拟器不能运行该原生模型。
- 真机视觉推理仍较慢，简单任务会优先走规则、playbook、页面文字和离线 RAG。
- App playbook 会随第三方 App 版本变化而失效；无法确认页面时会停止猜测并给出恢复提示。
- Android 系统 TTS 的声音与离线可用性由设备厂商和已安装语音包决定。
- 尚未完成跨两个 Android 厂商版本的全部 12 包网络关闭验收与老年用户可用性测试。

## 路线图

- 建立经过用户同意的多设备页面测试集。
- 对 12 个能力包执行完整网络关闭端到端测试。
- 分别测量规则/RAG 与 MiniCPM-V 路径的响应时间和任务完成率。
- 通过目标用户测试继续缩短文案、补齐恢复路径和 App 版本适配。
- 完成发布签名、依赖许可审查和正式版本分发流程。

详细里程碑见 [plan.md](plan.md)，界面与产品要求见 [docs/FRONTEND_PRODUCT_REQUIREMENTS.md](docs/FRONTEND_PRODUCT_REQUIREMENTS.md)。

## 参考项目

- [sherpa-onnx Android](https://k2-fsa.github.io/sherpa/onnx/android/index.html)
- [MiniCPM-V-Apps Android demo](https://github.com/OpenBMB/MiniCPM-V-Apps/tree/main/MiniCPM-V-demo-Android)
- [MiniCPM-V-Apps download notes](https://github.com/OpenBMB/MiniCPM-V-Apps/blob/main/DOWNLOAD_zh.md)
- [llama.cpp-omni](https://github.com/tc-mb/llama.cpp-omni)
