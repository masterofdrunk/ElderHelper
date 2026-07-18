package com.example.elderhelper.agent

/** Low-risk entry guidance for community and home services; it never places an order or pays. */
internal object CommunityServicePlanner {
    fun plan(userQuestion: String): AppFlowGuidance? {
        val question = userQuestion.lowercase().replace(Regex("\\s+"), "")
        val message = when {
            listOf("社区通知", "社区服务", "居委会").any(question::contains) ->
                "第一步：打开微信或支付宝，从搜索框输入你所在社区或街道的官方名称，再选择带有官方标识的服务入口。"
            listOf("助餐", "订老人餐", "送餐").any(question::contains) ->
                "第一步：打开社区服务、外卖或助餐 App，搜索附近助餐服务。提交订单前，请自己核对地址、餐品和金额。"
            listOf("家政", "保洁", "维修", "修水管", "修电器").any(question::contains) ->
                "第一步：在社区服务或生活服务 App 搜索家政、保洁或维修。不要相信私聊索要定金或验证码的人；下单前请核对服务商和价格。"
            else -> null
        }
        return message?.let {
            AppFlowGuidance(
                capabilityId = "community_service_entry",
                guidance = it,
                recoveryInstruction = "请回到微信、支付宝或社区服务 App 首页，再从搜索框查找官方服务。",
            )
        }
    }
}
