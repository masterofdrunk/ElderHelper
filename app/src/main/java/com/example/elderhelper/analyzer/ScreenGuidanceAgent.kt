package com.example.elderhelper.analyzer

import com.example.elderhelper.agent.AppPlaybookCatalog
import com.example.elderhelper.agent.AppFlowPlanner
import com.example.elderhelper.agent.CapabilityRegistry
import com.example.elderhelper.agent.CommunityServicePlanner
import com.example.elderhelper.agent.SafetyResiliencePlanner

/**
 * Routes a request before opening the expensive visual model. It only produces a local answer
 * when the evidence is explicit; uncertain requests deliberately fall through to MiniCPM-V.
 */
internal class ScreenGuidanceAgent(
    private val knowledgeRetriever: LocalKnowledgeRetriever = EmptyLocalKnowledgeRetriever,
) {
    fun plan(
        userQuestion: String,
        screenText: String?,
        sensitiveWarning: String,
        foregroundPackage: String? = null,
    ): ScreenGuidancePlan {
        val sanitizedText = ScreenTextSanitizer.sanitize(screenText)
        SafetyResiliencePlanner.plan(userQuestion, sanitizedText, foregroundPackage)?.let { safety ->
            return ScreenGuidancePlan.LocalAnswer(
                guidance = safety.message,
                taskId = safety.capabilityId,
                recoveryInstruction = safety.message,
            )
        }
        if (sensitiveWarning.isNotBlank()) {
            return ScreenGuidancePlan.LocalAnswer(
                "$sensitiveWarning 我不会替你确认付款、授权或输入凭证。请核对无误后，再由你本人操作。",
            )
        }

        val intent = ScreenGuidanceIntentClassifier.classify(
            userQuestion = userQuestion,
            screenText = sanitizedText,
            isSensitive = false,
        )
        if (isGreetingOrCapabilityQuestion(userQuestion)) {
            return ScreenGuidancePlan.LocalAnswer(
                "我可以帮你看当前屏幕、找按钮和解释页面。请直接说，例如：无线网络在哪里？",
            )
        }

        CommunityServicePlanner.plan(userQuestion)?.let { flow ->
            return ScreenGuidancePlan.LocalAnswer(
                guidance = flow.guidance,
                taskId = flow.capabilityId,
                recoveryInstruction = flow.recoveryInstruction,
            )
        }

        AppFlowPlanner.plan(foregroundPackage, userQuestion, sanitizedText)?.let { flow ->
            return ScreenGuidancePlan.LocalAnswer(
                guidance = flow.guidance,
                taskId = flow.capabilityId,
                recoveryInstruction = flow.recoveryInstruction,
            )
        }

        if (intent == ScreenGuidanceIntent.NAVIGATION) {
            findVisibleDestination(userQuestion, sanitizedText)?.let { destination ->
                return ScreenGuidancePlan.LocalAnswer(
                    "屏幕上有“$destination”。第一步：点击“$destination”。",
                    taskId = "screen-navigation:$destination",
                )
            }
        }

        val activePlaybook = AppPlaybookCatalog.find(foregroundPackage)
        knowledgeRetriever.retrieve(userQuestion, sanitizedText)?.let { answer ->
            val capability = CapabilityRegistry.find(answer.id)
            val guidance = if (activePlaybook?.supports(answer.id) == true) {
                "你现在正在使用${activePlaybook.displayName}。${answer.guidance}"
            } else {
                answer.guidance
            }
            return ScreenGuidancePlan.LocalAnswer(
                guidance = guidance,
                taskId = answer.id,
                recoveryInstruction = answer.recoveryInstruction ?: capability?.recoveryInstruction,
            )
        }

        if (intent == ScreenGuidanceIntent.SCREEN_IDENTIFICATION && sanitizedText.isNotBlank()) {
            val visibleItems = sanitizedText
                .split(Regex("[\\n，,；;。]"))
                .map(String::trim)
                .filter { it.length in 1..24 && it != "[已隐藏数字]" }
                .distinct()
                .take(3)
            if (visibleItems.isNotEmpty()) {
                return ScreenGuidancePlan.LocalAnswer(
                    "当前屏幕上能看到：${visibleItems.joinToString("、")}。",
                    taskId = "screen-identification",
                )
            }
        }

        return ScreenGuidancePlan.NeedsVisionModel
    }

    private fun isGreetingOrCapabilityQuestion(question: String): Boolean {
        val normalized = question.lowercase().replace(Regex("\\s+"), "")
        return normalized in setOf("你好", "嗨", "你是谁", "你能做什么", "你会做什么", "怎么用")
    }

    private fun findVisibleDestination(question: String, screenText: String): String? {
        if (screenText.isBlank()) return null
        return destinationLabels.firstOrNull { label ->
            question.contains(label, ignoreCase = true) && screenText.contains(label, ignoreCase = true)
        }
    }

    private companion object {
        val destinationLabels = listOf(
            "无线网络", "WLAN", "蓝牙", "移动网络", "设置", "微信", "支付宝", "扫一扫",
            "相机", "电话", "短信", "联系人", "健康码", "付款", "收款", "返回",
        )
    }
}

internal sealed interface ScreenGuidancePlan {
    data class LocalAnswer(
        val guidance: String,
        val taskId: String? = null,
        val recoveryInstruction: String? = null,
    ) : ScreenGuidancePlan

    data object NeedsVisionModel : ScreenGuidancePlan
}
