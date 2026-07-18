package com.example.elderhelper.analyzer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenGuidancePromptBuilderTest {
    @Test
    fun systemPromptDefinesOlderUserLocalScreenGuidanceBehavior() {
        val prompt = ScreenGuidancePromptBuilder.SYSTEM_PROMPT

        assertTrue(prompt.contains("老年人"))
        assertTrue(prompt.contains("当前手机截图"))
        assertTrue(prompt.contains("最多三步"))
        assertTrue(prompt.contains("不编造"))
        assertTrue(prompt.contains("验证码"))
        assertTrue(prompt.contains("支付"))
    }

    @Test
    fun userPromptPreservesQuestionAndRequestsScreenFirstAnswer() {
        val prompt = ScreenGuidancePromptBuilder.buildUserPrompt("  我现在连接的是哪个无线网络？  ")

        assertTrue(prompt.contains("先观察截图"))
        assertTrue(prompt.contains("我现在连接的是哪个无线网络？"))
        assertTrue(prompt.contains("第一步"))
        assertTrue(prompt.contains("截图不匹配"))
        assertFalse(prompt.contains("  我现在连接的是哪个无线网络？  "))
    }

    @Test
    fun userPromptIncludesSanitizedScreenContextAndIntent() {
        val prompt = ScreenGuidancePromptBuilder.buildUserPrompt(
            userQuestion = "这个页面怎么付款？",
            screenText = "订单金额 88 元，验证码 123456，确认付款",
            isSensitive = true,
        )

        assertTrue(prompt.contains("本次帮助类型：敏感操作"))
        assertTrue(prompt.contains("先提醒核对对象、金额和来源"))
        assertTrue(prompt.contains("订单金额 88 元"))
        assertTrue(prompt.contains("[已隐藏数字]"))
        assertFalse(prompt.contains("123456"))
    }
}
