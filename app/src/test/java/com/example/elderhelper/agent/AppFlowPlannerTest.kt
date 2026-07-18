package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFlowPlannerTest {
    @Test
    fun usesVisibleWechatLabelsBeforeAVisualModelFallback() {
        val flow = AppFlowPlanner.plan(
            foregroundPackage = "com.tencent.mm",
            userQuestion = "微信怎么发照片",
            screenText = "聊天\n加号\n相册",
        )

        assertEquals("wechat_send_photo", flow?.capabilityId)
        assertTrue(flow?.guidance?.contains("相册") == true)
    }

    @Test
    fun supportsLowRiskPhoneCameraAndMapEntryFlows() {
        assertEquals(
            "make_call",
            AppFlowPlanner.plan("com.android.dialer", "怎么打电话", "联系人\n拨号")?.capabilityId,
        )
        assertEquals(
            "camera_photo",
            AppFlowPlanner.plan("com.android.camera2", "我要拍照", "拍照\n录像")?.capabilityId,
        )
        assertEquals(
            "map_navigation",
            AppFlowPlanner.plan("com.autonavi.minimap", "怎么导航", "搜索地点\n路线")?.capabilityId,
        )
    }

    @Test
    fun keepsShoppingTravelAndHealthcareAtGuidanceOnlyBoundaries() {
        val delivery = AppFlowPlanner.plan("com.sankuai.meituan", "怎么点外卖", "外卖\n搜索")
        val ride = AppFlowPlanner.plan("com.sdu.didi.psnger", "我要叫车", "你要去哪")
        val health = AppFlowPlanner.plan("com.eg.android.AlipayGphone", "我要挂号", "医疗健康")

        assertEquals("food_delivery", delivery?.capabilityId)
        assertTrue(delivery?.guidance?.contains("付款页面前") == true)
        assertEquals("ride_hailing", ride?.capabilityId)
        assertTrue(ride?.guidance?.contains("自己核对") == true)
        assertEquals("healthcare_entry", health?.capabilityId)
        assertTrue(health?.guidance?.contains("身份认证") == true)
    }

    @Test
    fun supportsVideoSearchAndSafeAdvertisementExit() {
        val search = AppFlowPlanner.plan("com.ss.android.ugc.aweme", "帮我找视频", "搜索\n首页")
        val exit = AppFlowPlanner.plan("com.smile.gifmaker", "怎么关闭广告", "广告")

        assertEquals("video_search", search?.capabilityId)
        assertEquals("video_ad_exit", exit?.capabilityId)
        assertTrue(exit?.guidance?.contains("验证码") == true)
    }
}
