package com.example.elderhelper.agent

/** Deterministic safety paths that always take precedence over RAG and visual inference. */
internal object SafetyResiliencePlanner {
    fun plan(userQuestion: String, screenText: String?, foregroundPackage: String?): SafetyGuidance? {
        val text = listOf(userQuestion, screenText.orEmpty()).joinToString(" ").normalize()
        val appName = AppPlaybookCatalog.find(foregroundPackage)?.displayName

        return when {
            emergencyTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "emergency_call",
                message = "情况紧急时，请立刻拨打 120、110 或 119，或请身边的人帮忙。不要等待手机分析，也不要独自处理危险情况。",
            )
            scamTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "scam_interruption",
                message = buildString {
                    append("先立刻停止操作：不要转账、不要提供验证码，也不要开启屏幕共享或远程控制。")
                    appName?.let { append("请退出$it，") }
                    append("只通过 App 内官方客服或银行/平台官方电话核实。")
                },
            )
            lostPhoneTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "lost_phone_recovery",
                message = "手机丢失时，请先借家人手机联系运营商暂停号码，再用官方“查找设备”服务定位或锁定手机；随后修改重要账号密码。不要相信陌生人发来的找回链接。",
            )
            accountTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "account_recovery",
                message = "账号异常时，请只在这个 App 的官方登录页使用“找回密码”或联系官方客服。不要把验证码、密码或身份证照片发给任何人。",
            )
            privacyTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "privacy_settings",
                message = "请打开手机“设置”，找到“隐私”或“权限管理”。第一步：只检查你认识的 App，并关闭不需要的相机、麦克风或位置权限。",
            )
            familyHelpTerms.any(text::contains) -> SafetyGuidance(
                capabilityId = "family_handoff",
                message = "请先联系一位可信的家人。第一步：打开电话或微信，选择家人后发起语音或视频通话；不要把验证码告诉他人。",
            )
            else -> null
        }
    }

    private fun String.normalize(): String = lowercase().replace(Regex("\\s+"), "")

    private val emergencyTerms = listOf("救命", "紧急", "晕倒", "胸痛", "呼吸困难", "火灾", "报警", "打120", "打110", "打119")
    private val scamTerms = listOf("诈骗", "被骗", "陌生链接", "屏幕共享", "远程控制", "退款客服", "中奖", "刷流水", "安全账户", "先转账")
    private val lostPhoneTerms = listOf("手机丢了", "手机不见了", "丢手机", "找回手机")
    private val accountTerms = listOf("账号异常", "账号被盗", "密码忘了", "找回密码", "登录不上")
    private val privacyTerms = listOf("隐私设置", "关闭权限", "权限管理", "定位权限", "麦克风权限", "相机权限")
    private val familyHelpTerms = listOf("联系家人", "找家人帮忙", "给儿子打电话", "给女儿打电话")
}

internal data class SafetyGuidance(
    val capabilityId: String,
    val message: String,
)
