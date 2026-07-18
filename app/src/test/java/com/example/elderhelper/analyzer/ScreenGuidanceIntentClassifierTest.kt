package com.example.elderhelper.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGuidanceIntentClassifierTest {
    @Test
    fun classifiesCommonOlderUserQuestionsBeforeModelInference() {
        assertEquals(
            ScreenGuidanceIntent.NAVIGATION,
            ScreenGuidanceIntentClassifier.classify("无线网络在哪里打开", null, isSensitive = false),
        )
        assertEquals(
            ScreenGuidanceIntent.SCREEN_IDENTIFICATION,
            ScreenGuidanceIntentClassifier.classify("我现在连的是哪个无线网络", null, isSensitive = false),
        )
        assertEquals(
            ScreenGuidanceIntent.TROUBLESHOOTING,
            ScreenGuidanceIntentClassifier.classify("为什么微信打不开", null, isSensitive = false),
        )
        assertEquals(
            ScreenGuidanceIntent.SENSITIVE_OPERATION,
            ScreenGuidanceIntentClassifier.classify("帮我看看", "确认付款", isSensitive = false),
        )
    }

    @Test
    fun sanitizesLongNumbersBeforeTheyReachThePrompt() {
        assertEquals(
            "短信验证码 [已隐藏数字]，余额 28 元",
            ScreenTextSanitizer.sanitize("短信验证码 123456，余额 28 元"),
        )
    }

    @Test
    fun agentUsesExplicitScreenTextBeforeRequestingTheVisionModel() {
        val agent = ScreenGuidanceAgent()

        val directAnswer = agent.plan(
            userQuestion = "无线网络在哪里打开",
            screenText = "设置\n无线网络\n蓝牙",
            sensitiveWarning = "",
        )
        assertEquals(
            ScreenGuidancePlan.LocalAnswer(
                "屏幕上有“无线网络”。第一步：点击“无线网络”。",
                taskId = "screen-navigation:无线网络",
            ),
            directAnswer,
        )
        assertEquals(
            ScreenGuidancePlan.NeedsVisionModel,
            agent.plan("为什么打不开", "", sensitiveWarning = ""),
        )
    }

    @Test
    fun agentDoesNotSendSensitiveRequestsToTheVisionModel() {
        val plan = ScreenGuidanceAgent().plan(
            userQuestion = "帮我付款",
            screenText = "确认付款",
            sensitiveWarning = "先别急着操作。涉及付款、验证码、授权或账号安全时，请先确认对象、金额和来源是否可信。",
        )

        assertEquals(true, plan is ScreenGuidancePlan.LocalAnswer)
    }

    @Test
    fun agentReturnsAHighConfidenceKnowledgeAnswerBeforeTheVisionModel() {
        val agent = ScreenGuidanceAgent(
            LocalKnowledgeRetriever { question, _ ->
                if (question.contains("蓝牙")) LocalKnowledgeAnswer("bluetooth", "请打开蓝牙。") else null
            },
        )

        assertEquals(
            ScreenGuidancePlan.LocalAnswer(
                guidance = "请打开蓝牙。",
                taskId = "bluetooth",
                recoveryInstruction = "请回到设置首页，找到“蓝牙”后重新尝试。",
            ),
            agent.plan("蓝牙怎么打开", "", sensitiveWarning = ""),
        )
    }
}
