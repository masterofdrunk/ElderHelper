package com.example.elderhelper.agent

/**
 * Maps the foreground Android package to the locally verified guidance scope. Package matching
 * is deliberately exact: an unknown or changed app falls back to generic RAG or visual analysis.
 */
internal object AppPlaybookCatalog {
    fun find(packageName: String?): AppPlaybook? {
        if (packageName.isNullOrBlank()) return null
        return playbooks.firstOrNull { packageName in it.packageNames }
    }

    private val playbooks = listOf(
        AppPlaybook(
            id = "android-settings",
            displayName = "系统设置",
            packageNames = setOf("com.android.settings"),
            supportedTaskIds = setOf(
                "wifi_connect", "wifi_troubleshoot", "mobile_data", "bluetooth", "volume",
                "brightness", "font_size", "flashlight", "battery_saver", "airplane_mode",
                "hotspot", "app_permissions",
            ),
        ),
        AppPlaybook(
            id = "wechat",
            displayName = "微信",
            packageNames = setOf("com.tencent.mm"),
            supportedTaskIds = setOf("wechat_voice_video", "wechat_send_photo", "wechat_voice_message"),
        ),
        AppPlaybook(
            id = "phone",
            displayName = "电话",
            packageNames = setOf("com.android.dialer", "com.google.android.dialer"),
            supportedTaskIds = setOf("answer_call", "make_call"),
        ),
        AppPlaybook(
            id = "messages",
            displayName = "短信",
            packageNames = setOf("com.android.mms", "com.google.android.apps.messaging"),
            supportedTaskIds = setOf("send_sms"),
        ),
        AppPlaybook(
            id = "camera",
            displayName = "相机",
            packageNames = setOf("com.android.camera2", "com.huawei.camera", "com.hihonor.camera"),
            supportedTaskIds = setOf("camera_photo"),
        ),
        AppPlaybook(
            id = "photos",
            displayName = "相册",
            packageNames = setOf("com.google.android.apps.photos", "com.huawei.photos", "com.hihonor.photos"),
            supportedTaskIds = setOf("photo_album"),
        ),
        AppPlaybook("alipay", "支付宝", setOf("com.eg.android.AlipayGphone"), emptySet(), highRisk = true),
        AppPlaybook("amap", "高德地图", setOf("com.autonavi.minimap"), emptySet()),
        AppPlaybook("baidu-maps", "百度地图", setOf("com.baidu.BaiduMap"), emptySet()),
        AppPlaybook("didi", "滴滴出行", setOf("com.sdu.didi.psnger"), emptySet(), highRisk = true),
        AppPlaybook("railway12306", "铁路12306", setOf("com.MobileTicket"), emptySet(), highRisk = true),
        AppPlaybook("taobao", "淘宝", setOf("com.taobao.taobao"), emptySet(), highRisk = true),
        AppPlaybook("jd", "京东", setOf("com.jingdong.app.mall"), emptySet(), highRisk = true),
        AppPlaybook("pinduoduo", "拼多多", setOf("com.xunmeng.pinduoduo"), emptySet(), highRisk = true),
        AppPlaybook("meituan", "美团", setOf("com.sankuai.meituan"), emptySet(), highRisk = true),
        AppPlaybook("eleme", "饿了么", setOf("me.ele"), emptySet(), highRisk = true),
        AppPlaybook("douyin", "抖音", setOf("com.ss.android.ugc.aweme"), emptySet()),
        AppPlaybook("kuaishou", "快手", setOf("com.smile.gifmaker"), emptySet()),
    )
}

internal data class AppPlaybook(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
    val supportedTaskIds: Set<String>,
    val highRisk: Boolean = false,
) {
    fun supports(taskId: String): Boolean = taskId in supportedTaskIds
}
