package com.example.elderhelper.analyzer

/** A small, deterministic planning layer before the on-device vision model. */
internal enum class ScreenGuidanceIntent(
    val displayName: String,
    val responseFocus: String,
) {
    NAVIGATION("页面导航", "告诉用户先看哪里、再点哪里；优先给出一个明确动作。"),
    SCREEN_IDENTIFICATION("识别当前页面", "直接说明当前页面或状态；没有看清就明确说明。"),
    TROUBLESHOOTING("排查问题", "先说明屏幕上能确认的状态，再给一个风险低的排查动作。"),
    SENSITIVE_OPERATION("敏感操作", "先提醒核对对象、金额和来源；不引导最终付款、授权或泄露凭证。"),
    GENERAL_HELP("一般帮助", "根据截图中可见的信息给出简短、具体的下一步。"),
}

internal object ScreenGuidanceIntentClassifier {
    fun classify(
        userQuestion: String,
        screenText: String?,
        isSensitive: Boolean,
    ): ScreenGuidanceIntent {
        if (isSensitive) return ScreenGuidanceIntent.SENSITIVE_OPERATION

        val question = userQuestion.normalize()
        val combinedText = listOf(userQuestion, screenText.orEmpty())
            .joinToString(" ")
            .normalize()

        return when {
            identificationKeywords.any(question::contains) -> ScreenGuidanceIntent.SCREEN_IDENTIFICATION
            troubleshootingKeywords.any(question::contains) -> ScreenGuidanceIntent.TROUBLESHOOTING
            navigationKeywords.any(question::contains) -> ScreenGuidanceIntent.NAVIGATION
            sensitiveScreenKeywords.any(combinedText::contains) -> ScreenGuidanceIntent.SENSITIVE_OPERATION
            else -> ScreenGuidanceIntent.GENERAL_HELP
        }
    }

    private fun String.normalize(): String = lowercase().replace(Regex("\\s+"), "")

    private val identificationKeywords = listOf(
        "这是什么", "这是哪里", "当前页面", "现在是什么", "显示什么", "连的是哪个",
        "什么网络", "什么状态", "有没有打开", "开了吗",
    )
    private val troubleshootingKeywords = listOf(
        "怎么不行", "不能", "失败", "出错", "打不开", "没有反应", "找不到", "连不上",
        "收不到", "听不到", "卡住", "怎么办",
    )
    private val navigationKeywords = listOf(
        "怎么", "哪里", "怎么点", "打开", "关闭", "进入", "返回", "设置", "下一步", "帮我找",
    )
    private val sensitiveScreenKeywords = listOf(
        "付款", "支付", "转账", "收款", "验证码", "授权", "登录", "密码", "银行卡", "身份证",
    )
}

/**
 * Text from a future OCR or Accessibility source is useful context, but it must not cause a
 * verification code, card number, or other long numeric value to be repeated by the model.
 */
internal object ScreenTextSanitizer {
    private const val MAX_CONTEXT_CHARS = 800
    private val longNumber = Regex("(?<!\\d)\\d{6,}(?!\\d)")

    fun sanitize(screenText: String?): String {
        return screenText
            .orEmpty()
            .replace(longNumber, "[已隐藏数字]")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CONTEXT_CHARS)
    }
}
