package com.example.elderhelper.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opt-in workflow teaching harness. It intentionally captures semantic UI structure only and
 * refuses to create drafts from high-risk applications or sensitive controls.
 */
internal object WorkflowTeachingHarness {
    private var activeSession: TeachingSession? = null
    private var blockedReason: String? = null

    @Synchronized
    fun start() {
        activeSession = TeachingSession()
        blockedReason = null
    }

    @Synchronized
    fun isActive(): Boolean = activeSession != null

    @Synchronized
    fun setGoalFromUserQuestion(question: String) {
        activeSession?.intent = LearnedWorkflowIntent.fromQuestion(question)
    }

    @Synchronized
    fun recordClick(packageName: String?, viewId: String?, visibleLabel: String?, isEditable: Boolean) {
        val session = activeSession ?: return
        if (isEditable || packageName.isNullOrBlank()) return
        if (isHighRiskPackage(packageName) || isSensitiveLabel(visibleLabel)) {
            blockedReason = "这个页面可能涉及账号、支付或隐私信息，已停止学习且没有保存内容。"
            activeSession = null
            return
        }

        val stableViewId = viewId?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: return
        val safeLabel = visibleLabel?.takeIf(::isAllowListedLabel)
        val step = LearnedWorkflowStep(packageName, stableViewId, safeLabel)
        if (session.steps.lastOrNull() == step || session.steps.size >= MAX_STEPS) return
        session.steps += step
    }

    @Synchronized
    fun finish(): TeachingFinish {
        blockedReason?.let { reason ->
            blockedReason = null
            return TeachingFinish.Blocked(reason)
        }
        val session = activeSession.also { activeSession = null }
            ?: return TeachingFinish.Empty("还没有开始“教我一次”。")
        val intent = session.intent ?: return TeachingFinish.Empty("请先用悬浮助手说出要教的操作，再完成实际点击。")
        if (
            intent == LearnedWorkflowIntent.GENERIC_LOW_RISK ||
            intent.capabilityId()?.let(CapabilityRegistry::isLearningAllowed) != true ||
            session.steps.isEmpty()
        ) {
            return TeachingFinish.Empty("这次没有收集到可安全复用的操作步骤。")
        }
        return TeachingFinish.Draft(
            LearnedWorkflowDraft(
                intent = intent,
                primaryPackageName = session.steps.first().packageName,
                steps = session.steps.toList(),
            ),
        )
    }

    @Synchronized
    fun discard() {
        activeSession = null
        blockedReason = null
    }

    private fun isHighRiskPackage(packageName: String): Boolean {
        return AppPlaybookCatalog.find(packageName)?.highRisk == true ||
            packageName.contains("bank", ignoreCase = true) ||
            packageName.contains("wallet", ignoreCase = true)
    }

    private fun isSensitiveLabel(label: String?): Boolean {
        val text = label.orEmpty().lowercase()
        return sensitiveWords.any(text::contains)
    }

    private fun isAllowListedLabel(label: String): Boolean = label.trim() in safeLabels

    private data class TeachingSession(
        var intent: LearnedWorkflowIntent? = null,
        val steps: MutableList<LearnedWorkflowStep> = mutableListOf(),
    )

    private const val MAX_STEPS = 12
    private val sensitiveWords = listOf("密码", "验证码", "付款", "支付", "转账", "银行卡", "身份证", "人脸")
    private val safeLabels = setOf(
        "加号", "+", "相册", "照片", "发送", "视频通话", "语音通话", "按住说话",
        "无线网络", "WLAN", "Wi-Fi", "蓝牙", "相机", "图库", "电话", "联系人", "信息",
    )
}

internal enum class LearnedWorkflowIntent(val displayName: String) {
    WECHAT_SEND_PHOTO("微信发送照片"),
    WECHAT_VOICE_VIDEO("微信语音或视频通话"),
    WIFI_CONNECT("连接无线网络"),
    BLUETOOTH_CONNECT("连接蓝牙设备"),
    CAMERA_TAKE_PHOTO("使用相机拍照"),
    GENERIC_LOW_RISK("低风险操作"),
    ;

    companion object {
        fun fromQuestion(question: String): LearnedWorkflowIntent {
            val text = question.lowercase().replace(Regex("\\s+"), "")
            return when {
                text.contains("微信") && (text.contains("照片") || text.contains("图片") || text.contains("相片")) -> WECHAT_SEND_PHOTO
                text.contains("微信") && (text.contains("视频") || text.contains("语音")) -> WECHAT_VOICE_VIDEO
                text.contains("无线网络") || text.contains("wifi") || text.contains("wlan") -> WIFI_CONNECT
                text.contains("蓝牙") -> BLUETOOTH_CONNECT
                text.contains("拍照") || text.contains("相机") -> CAMERA_TAKE_PHOTO
                else -> GENERIC_LOW_RISK
            }
        }
    }

    fun capabilityId(): String? = when (this) {
        WECHAT_SEND_PHOTO -> "wechat_send_photo"
        WECHAT_VOICE_VIDEO -> "wechat_voice_video"
        WIFI_CONNECT -> "wifi_connect"
        BLUETOOTH_CONNECT -> "bluetooth"
        CAMERA_TAKE_PHOTO -> "camera_photo"
        GENERIC_LOW_RISK -> null
    }
}

internal data class LearnedWorkflowStep(
    val packageName: String,
    val viewId: String,
    val safeLabel: String?,
) {
    fun spokenStep(index: Int): String =
        safeLabel?.let { "第$index 步：点击“$it”。" } ?: "第$index 步：点击当前页面的对应按钮。"
}

internal data class LearnedWorkflowDraft(
    val intent: LearnedWorkflowIntent,
    val primaryPackageName: String,
    val steps: List<LearnedWorkflowStep>,
) {
    fun reviewText(): String = buildString {
        append("学习目标：${intent.displayName}\n\n")
        steps.forEachIndexed { index, step -> append(step.spokenStep(index + 1)).append('\n') }
        append("\n只会保存这些通用操作步骤，不会保存聊天内容、联系人、照片、语音或截图。")
    }

    fun replayGuidance(): String = buildString {
        append("我学过“${intent.displayName}”。")
        steps.forEachIndexed { index, step -> append(step.spokenStep(index + 1)) }
    }
}

internal sealed interface TeachingFinish {
    data class Draft(val workflow: LearnedWorkflowDraft) : TeachingFinish

    data class Empty(val message: String) : TeachingFinish

    data class Blocked(val message: String) : TeachingFinish
}

internal class LearnedWorkflowStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(workflow: LearnedWorkflowDraft) {
        val root = JSONObject().apply {
            put("intent", workflow.intent.name)
            put("packageName", workflow.primaryPackageName)
            put("appVersion", installedVersion(workflow.primaryPackageName))
            put("savedAtMs", System.currentTimeMillis())
            put("steps", JSONArray().apply {
                workflow.steps.forEach { step ->
                    put(JSONObject().apply {
                        put("packageName", step.packageName)
                        put("viewId", step.viewId)
                        put("safeLabel", step.safeLabel)
                    })
                }
            })
        }
        preferences.edit().putString(workflow.intent.name, root.toString()).apply()
    }

    fun find(intent: LearnedWorkflowIntent, foregroundPackage: String?): LearnedWorkflowDraft? {
        if (intent == LearnedWorkflowIntent.GENERIC_LOW_RISK) return null
        val root = preferences.getString(intent.name, null)?.let(::JSONObject) ?: return null
        val packageName = root.getString("packageName")
        if (foregroundPackage != packageName) return null
        if (System.currentTimeMillis() - root.optLong("savedAtMs", 0L) > MAX_WORKFLOW_AGE_MS) return null
        if (root.optLong("appVersion", -1L) != installedVersion(packageName)) return null
        val steps = root.getJSONArray("steps")
        return LearnedWorkflowDraft(
            intent = LearnedWorkflowIntent.valueOf(root.getString("intent")),
            primaryPackageName = packageName,
            steps = List(steps.length()) { index ->
                val step = steps.getJSONObject(index)
                LearnedWorkflowStep(
                    packageName = step.getString("packageName"),
                    viewId = step.getString("viewId"),
                    safeLabel = step.optString("safeLabel").takeIf(String::isNotBlank),
                )
            },
        )
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun installedVersion(packageName: String): Long {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: Exception) {
            -1L
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "learned_workflows"
        const val MAX_WORKFLOW_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
