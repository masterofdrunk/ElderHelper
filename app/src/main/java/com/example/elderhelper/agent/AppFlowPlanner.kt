package com.example.elderhelper.agent

/**
 * Deterministic, low-latency flows for a small set of high-frequency Apps. A flow only uses the
 * visible labels it can verify; unknown pages fall back instead of inventing a control.
 */
internal object AppFlowPlanner {
    fun plan(
        foregroundPackage: String?,
        userQuestion: String,
        screenText: String?,
    ): AppFlowGuidance? {
        val playbook = AppPlaybookCatalog.find(foregroundPackage) ?: return null
        val question = userQuestion.normalize()
        val visibleText = screenText.orEmpty()

        return when (playbook.id) {
            "wechat" -> wechatFlow(question, visibleText)
            "phone" -> phoneFlow(question, visibleText)
            "messages" -> messagesFlow(question, visibleText)
            "camera" -> cameraFlow(question, visibleText)
            "photos" -> photosFlow(question, visibleText)
            "amap", "baidu-maps" -> navigationFlow(playbook.displayName, question, visibleText)
            "douyin", "kuaishou" -> shortVideoFlow(playbook.displayName, question, visibleText)
            "meituan", "eleme" -> deliveryFlow(playbook.displayName, question, visibleText)
            "taobao", "jd", "pinduoduo" -> shoppingFlow(playbook.displayName, question, visibleText)
            "didi" -> rideHailingFlow(question, visibleText)
            "railway12306" -> railwayFlow(question, visibleText)
            "alipay" -> alipayServiceFlow(question, visibleText)
            else -> null
        }
    }

    private fun wechatFlow(question: String, visibleText: String): AppFlowGuidance? = when {
        question.hasAny("发照片", "发图片", "发相片") -> when {
            visibleText.hasAny("相册", "照片") -> flow(
                "wechat_send_photo",
                "你现在在微信里。第一步：点击“相册”或“照片”，选择要发送的图片后再点发送。",
            )
            visibleText.hasAny("加号", "更多功能") -> flow(
                "wechat_send_photo",
                "第一步：点击聊天输入框旁边的加号，再选择“相册”。",
            )
            else -> flow(
                "wechat_send_photo",
                "请先进入要联系人的微信聊天页面。第一步：点击输入框旁边的加号，再选择“相册”。",
            )
        }
        question.hasAny("视频通话", "语音通话", "微信视频", "微信语音") -> when {
            visibleText.hasAny("视频通话", "语音通话") -> flow(
                "wechat_voice_video",
                "第一步：点击屏幕上的“视频通话”或“语音通话”。",
            )
            visibleText.hasAny("加号", "更多功能") -> flow(
                "wechat_voice_video",
                "第一步：点击聊天输入框旁边的加号，再选择“视频通话”或“语音通话”。",
            )
            else -> flow(
                "wechat_voice_video",
                "请先进入要联系人的微信聊天页面。第一步：点击加号，再选择“视频通话”或“语音通话”。",
            )
        }
        question.hasAny("发语音", "语音消息") -> when {
            visibleText.hasAny("按住说话") -> flow(
                "wechat_voice_message",
                "第一步：按住“按住说话”，说完后松开就会发送。",
            )
            visibleText.hasAny("语音输入", "切换到按住说话") -> flow(
                "wechat_voice_message",
                "第一步：点击语音输入图标，切换到“按住说话”。",
            )
            else -> flow(
                "wechat_voice_message",
                "请先进入微信聊天页面。第一步：点击输入框旁边的语音图标，找到“按住说话”。",
            )
        }
        question.hasAny("视频号", "看视频", "找视频") -> when {
            visibleText.hasAny("搜索", "搜索视频") -> flow(
                "video_search",
                "第一步：点击搜索，输入想看的内容，再从结果中选择视频。不要点击陌生人发来的领奖或退款链接。",
            )
            else -> flow(
                "video_search",
                "第一步：在微信底部找到“发现”或“视频号”，再使用搜索功能找内容。不要点击陌生人发来的领奖或退款链接。",
            )
        }
        else -> null
    }

    private fun phoneFlow(question: String, visibleText: String): AppFlowGuidance? = when {
        question.hasAny("打电话", "拨电话", "联系人电话") -> when {
            visibleText.hasAny("联系人") -> flow("make_call", "第一步：点击“联系人”，再选择要联系的人。")
            visibleText.hasAny("拨号", "键盘") -> flow("make_call", "第一步：点击“拨号”或数字键盘，输入号码后点击电话图标。")
            else -> flow("make_call", "第一步：在电话 App 中找到“联系人”或“拨号”。")
        }
        question.hasAny("接电话", "接听", "挂电话") -> flow(
            "answer_call",
            "来电时，第一步：滑动或点击绿色接听按钮；不接就点击红色挂断按钮。",
        )
        else -> null
    }

    private fun messagesFlow(question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("发短信", "写短信", "短信")) return null
        return if (visibleText.hasAny("新建信息", "新信息", "写信息")) {
            flow("send_sms", "第一步：点击“新建信息”或“写信息”，再选择收件人。")
        } else {
            flow("send_sms", "第一步：在短信页面点击新建按钮，再选择收件人。")
        }
    }

    private fun cameraFlow(question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("拍照", "照相")) return null
        return if (visibleText.hasAny("拍照")) {
            flow("camera_photo", "第一步：确认在“拍照”模式后，点击屏幕下方的圆形快门按钮。")
        } else {
            flow("camera_photo", "第一步：找到屏幕下方的圆形快门按钮，点击它拍照。")
        }
    }

    private fun photosFlow(question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("看照片", "找照片", "相册在哪里", "照片在哪")) return null
        return if (visibleText.hasAny("相册", "图库", "照片")) {
            flow("photo_album", "第一步：点击屏幕上的“相册”“图库”或“照片”，查看以前拍过的图片。")
        } else {
            flow("photo_album", "第一步：回到桌面，找到“相册”“图库”或“照片”后点击。")
        }
    }

    private fun navigationFlow(appName: String, question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("导航", "路线", "怎么去", "地图")) return null
        return if (visibleText.hasAny("搜索", "搜索地点", "输入地点")) {
            flow("map_navigation", "你现在在$appName。第一步：点击搜索地点，输入要去的地方后选择路线。")
        } else {
            flow("map_navigation", "你现在在$appName。第一步：找到顶部的搜索框，输入要去的地方后选择路线。")
        }
    }

    private fun shortVideoFlow(appName: String, question: String, visibleText: String): AppFlowGuidance? = when {
        question.hasAny("关闭广告", "跳过广告", "不想看广告") -> flow(
            "video_ad_exit",
            "你现在在$appName。第一步：找视频角落的“关闭”“跳过”或叉号。不要为了领取奖励而点击陌生链接、下载其他 App 或提供验证码。",
        )
        question.hasAny("找视频", "搜索视频", "看视频") -> {
            val guidance = if (visibleText.hasAny("搜索", "放大镜")) {
                "你现在在$appName。第一步：点击搜索或放大镜，输入想看的内容后选择视频。"
            } else {
                "你现在在$appName。第一步：回到首页，找到搜索或放大镜图标后输入想看的内容。"
            }
            flow("video_search", guidance)
        }
        else -> null
    }

    private fun deliveryFlow(appName: String, question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("点外卖", "订餐", "买菜", "外卖")) return null
        val guidance = if (visibleText.hasAny("搜索", "外卖", "买菜")) {
            "你现在在$appName。第一步：点击搜索框，选择商家或商品。到付款页面前，请自己核对金额、地址和商家。"
        } else {
            "你现在在$appName。第一步：回到首页，找到“外卖”或搜索框后再选择商家。到付款页面前，请自己核对金额、地址和商家。"
        }
        return flow("food_delivery", guidance)
    }

    private fun shoppingFlow(appName: String, question: String, visibleText: String): AppFlowGuidance? = when {
        question.hasAny("查订单", "订单在哪", "物流", "快递到哪") -> flow(
            "order_tracking",
            "你现在在$appName。第一步：点击“我的”或“订单”，再选择需要查看的订单。不要在陌生客服页面输入验证码。",
        )
        question.hasAny("买东西", "找商品", "购物", "搜索商品") -> {
            val guidance = if (visibleText.hasAny("搜索", "搜索框")) {
                "你现在在$appName。第一步：点击搜索框，输入要买的商品。到付款前，请自己核对商品、价格和收货地址。"
            } else {
                "你现在在$appName。第一步：回到首页，找到顶部搜索框后输入商品名称。到付款前，请自己核对商品、价格和收货地址。"
            }
            flow("shopping_search", guidance)
        }
        else -> null
    }

    private fun rideHailingFlow(question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("叫车", "打车", "网约车")) return null
        val guidance = if (visibleText.hasAny("你要去哪", "输入目的地", "目的地")) {
            "第一步：点击目的地输入框，填写要去的地方。叫车前，请自己核对上车点、目的地和预估价格。"
        } else {
            "请回到滴滴首页。第一步：找到“你要去哪”或目的地输入框。叫车前，请自己核对上车点、目的地和预估价格。"
        }
        return flow("ride_hailing", guidance)
    }

    private fun railwayFlow(question: String, visibleText: String): AppFlowGuidance? {
        if (!question.hasAny("查车票", "买火车票", "火车票", "高铁票")) return null
        val guidance = if (visibleText.hasAny("出发地", "目的地", "查询")) {
            "第一步：填写出发地、目的地和日期后点击查询。提交订单或付款前，请自己核对乘车人、日期和金额。"
        } else {
            "请回到 12306 首页。第一步：找到出发地和目的地，再填写日期查询车票。提交订单或付款前，请自己核对信息。"
        }
        return flow("ticket_search", guidance)
    }

    private fun alipayServiceFlow(question: String, visibleText: String): AppFlowGuidance? = when {
        question.hasAny("挂号", "医疗", "医保", "健康") -> {
            val guidance = if (visibleText.hasAny("医疗健康", "医保", "健康")) {
                "第一步：点击“医疗健康”或“医保”入口。涉及身份认证、授权、挂号确认或付款时，请由你本人核对后操作。"
            } else {
                "请回到支付宝首页。第一步：从搜索框或服务页找到“医疗健康”或“医保”。涉及身份认证、授权、挂号确认或付款时，请由你本人核对后操作。"
            }
            flow("healthcare_entry", guidance)
        }
        question.hasAny("社保", "政务", "办事") -> flow(
            "civic_service_entry",
            "请从支付宝服务页或搜索框找到当地“政务服务”或“社保医保”入口。涉及身份认证或授权时，请由你本人核对后操作。",
        )
        else -> null
    }

    private fun flow(capabilityId: String, guidance: String): AppFlowGuidance {
        return AppFlowGuidance(
            capabilityId = capabilityId,
            guidance = guidance,
            recoveryInstruction = CapabilityRegistry.find(capabilityId)?.recoveryInstruction
                ?: "请回到这个 App 的首页，再告诉我屏幕上能看到什么。",
        )
    }

    private fun String.normalize(): String = lowercase().replace(Regex("\\s+"), "")

    private fun String.hasAny(vararg values: String): Boolean = values.any(::contains)
}

internal data class AppFlowGuidance(
    val capabilityId: String,
    val guidance: String,
    val recoveryInstruction: String,
)
