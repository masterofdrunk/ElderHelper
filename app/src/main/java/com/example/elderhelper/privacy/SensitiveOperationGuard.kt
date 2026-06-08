package com.example.elderhelper.privacy

class SensitiveOperationGuard {
    fun warningFor(userQuestion: String, screenText: String?): String {
        val text = listOf(userQuestion, screenText.orEmpty())
            .joinToString(separator = " ")
            .lowercase()

        val isSensitive = sensitiveKeywords.any { keyword -> text.contains(keyword) }
        if (!isSensitive) return ""

        return "先别急着操作。涉及付款、验证码、授权或账号安全时，请先确认对象、金额和来源是否可信。"
    }

    private companion object {
        val sensitiveKeywords = listOf(
            "付款",
            "支付",
            "转账",
            "收款",
            "验证码",
            "授权",
            "登录",
            "密码",
            "银行卡",
            "身份证",
            "医保",
            "医疗",
            "法律",
            "pay",
            "code",
            "password",
        )
    }
}
