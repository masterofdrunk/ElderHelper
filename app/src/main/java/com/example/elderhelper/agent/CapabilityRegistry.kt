package com.example.elderhelper.agent

/** Single source of truth for tasks that ElderHelper is allowed to guide locally. */
internal object CapabilityRegistry {
    fun find(id: String): CapabilityDefinition? = definitions[id]

    fun isLearningAllowed(id: String): Boolean = definitions[id]?.learningAllowed == true

    private val learnableCapabilityIds = setOf(
        "wifi_connect", "bluetooth", "wechat_send_photo", "wechat_voice_video", "camera_photo",
    )

    private val definitions = listOf(
        capability("wifi_connect", "设备与无障碍", "android-settings", "请回到设置首页，再找“无线网络”或“WLAN”。"),
        capability("wifi_troubleshoot", "设备与无障碍", "android-settings", "请回到无线网络页面，确认 Wi-Fi 已打开并重新选择网络。"),
        capability("mobile_data", "设备与无障碍", "android-settings", "请回到设置首页，找到“移动网络”或“移动数据”。"),
        capability("bluetooth", "设备与无障碍", "android-settings", "请回到设置首页，找到“蓝牙”后重新尝试。"),
        capability("volume", "设备与无障碍", "android-settings", "请先按侧边音量加键；如果没有变化，再回到“声音和振动”。"),
        capability("brightness", "设备与无障碍", "android-settings", "从屏幕顶部向下滑，再找到亮度滑条。"),
        capability("font_size", "设备与无障碍", "android-settings", "请回到“显示和亮度”，再找“字体大小”或“显示大小”。"),
        capability("flashlight", "设备与无障碍", null, "从屏幕顶部向下滑，在快捷设置里找“手电筒”。"),
        capability("battery_saver", "设备与无障碍", "android-settings", "请回到设置首页，找到“电池”或“省电模式”。"),
        capability("airplane_mode", "设备与无障碍", null, "从屏幕顶部向下滑，找到“飞行模式”开关。"),
        capability("hotspot", "设备与无障碍", "android-settings", "请回到设置首页，找到“个人热点”或“便携式热点”。"),
        capability("app_permissions", "设备与无障碍", "android-settings", "请回到设置首页，找到“应用管理”或“权限管理”。"),
        capability("answer_call", "电话与联系人", null, "请回到来电页面；要接听就找绿色电话按钮。"),
        capability("make_call", "电话与联系人", null, "请回到电话或联系人页面，再选择要联系的人。"),
        capability("send_sms", "电话与联系人", null, "请回到信息或短信页面，再点新建消息。"),
        capability("wechat_voice_video", "家庭与社交", "wechat", "请回到和这位联系人的微信聊天页面，再点加号。"),
        capability("wechat_send_photo", "家庭与社交", "wechat", "请回到微信聊天页面，再点加号并选择“相册”。"),
        capability("wechat_voice_message", "家庭与社交", "wechat", "请回到微信聊天页面，找到“按住说话”。"),
        capability("map_navigation", "出行", null, "请回到地图 App 首页，重新从搜索框输入目的地。"),
        guidedCapability("food_delivery", "购物与日常服务", "请回到外卖 App 首页，重新从搜索框选择商家或商品。"),
        guidedCapability("shopping_search", "购物与日常服务", "请回到购物 App 首页，从搜索框重新找商品。"),
        guidedCapability("order_tracking", "购物与日常服务", "请回到“我的订单”，再选择需要查看的订单。"),
        guidedCapability("ride_hailing", "出行", "请回到叫车 App 首页，重新确认上车点和目的地。"),
        guidedCapability("ticket_search", "出行", "请回到铁路 App 首页，重新输入出发地、目的地和日期。"),
        guidedCapability("healthcare_entry", "健康与照护", "请回到服务首页，找到“医疗健康”“挂号”或“医保”入口。"),
        guidedCapability("civic_service_entry", "政府与公共服务", "请回到服务首页，找到当地政务或社保医保入口。"),
        guidedCapability("video_search", "新闻娱乐学习", "请回到视频 App 首页，再通过搜索功能找内容。"),
        guidedCapability("video_ad_exit", "新闻娱乐学习", "请关闭当前广告；遇到领奖、退款或验证码提示时直接退出。"),
        guidedCapability("community_service_entry", "家庭与社区", "请通过官方社区或生活服务入口查找服务，并在付款前自行核对。"),
        capability("camera_photo", "相机照片文件", null, "请回到相机页面，找到屏幕下方的圆形拍照按钮。"),
        capability("photo_album", "相机照片文件", null, "请回到桌面，找“相册”“图库”或“照片”。"),
        capability("screenshot", "相机照片文件", null, "请同时按住电源键和音量减键，再试一次。"),
        CapabilityDefinition(
            id = "emergency_call",
            category = "安全与恢复",
            riskLevel = CapabilityRisk.URGENT,
            preferredPlaybookId = null,
            recoveryInstruction = "如果情况紧急，请直接拨打 120、110 或当地紧急电话，不要等待 App 分析。",
            learningAllowed = false,
        ),
        guidanceOnly("scam_interruption", "安全与恢复", "已停止普通引导。请退出当前页面，只通过官方渠道核实。"),
        guidanceOnly("lost_phone_recovery", "安全与恢复", "请借用可信手机联系运营商并使用官方查找设备服务。"),
        guidanceOnly("account_recovery", "安全与恢复", "请只在官方 App 或官网使用找回账号功能。"),
        guidanceOnly("privacy_settings", "安全与恢复", "请回到系统设置中的隐私或权限管理页面。"),
        guidanceOnly("family_handoff", "安全与恢复", "请联系可信家人协助，不要向陌生人泄露验证码。"),
    ).associateBy(CapabilityDefinition::id)

    private fun capability(
        id: String,
        category: String,
        preferredPlaybookId: String?,
        recoveryInstruction: String,
    ): CapabilityDefinition = CapabilityDefinition(
        id = id,
        category = category,
        riskLevel = CapabilityRisk.LOW,
        preferredPlaybookId = preferredPlaybookId,
        recoveryInstruction = recoveryInstruction,
        learningAllowed = id in learnableCapabilityIds,
    )

    private fun guidedCapability(
        id: String,
        category: String,
        recoveryInstruction: String,
    ): CapabilityDefinition = CapabilityDefinition(
        id = id,
        category = category,
        riskLevel = CapabilityRisk.GUIDANCE_ONLY,
        preferredPlaybookId = null,
        recoveryInstruction = recoveryInstruction,
        learningAllowed = false,
    )

    private fun guidanceOnly(
        id: String,
        category: String,
        recoveryInstruction: String,
    ): CapabilityDefinition = guidedCapability(id, category, recoveryInstruction)

}

internal data class CapabilityDefinition(
    val id: String,
    val category: String,
    val riskLevel: CapabilityRisk,
    val preferredPlaybookId: String?,
    val recoveryInstruction: String,
    val learningAllowed: Boolean,
)

internal enum class CapabilityRisk {
    LOW,
    GUIDANCE_ONLY,
    URGENT,
}
