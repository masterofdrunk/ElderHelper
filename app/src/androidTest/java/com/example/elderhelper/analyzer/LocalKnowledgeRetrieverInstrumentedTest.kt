package com.example.elderhelper.analyzer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalKnowledgeRetrieverInstrumentedTest {
    @Test
    fun loadsBundledOfflineKnowledgeWithoutOpeningTheVisionModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val retriever = AssetLocalKnowledgeRetriever(context)
        val answer = retriever.retrieve("蓝牙怎么打开", "")

        assertNotNull(answer)
        assertEquals("bluetooth", answer?.id)
        assertEquals("先打开“设置”，找到“蓝牙”。第一步：点击“蓝牙”，打开开关后选择要连接的设备。", answer?.guidance)

        val visibleAnswer = retriever.retrieve("蓝牙怎么打开", "设置\n蓝牙\n已配对设备")
        assertEquals("屏幕上有“蓝牙”。第一步：点击“蓝牙”，打开开关后选择要连接的设备。", visibleAnswer?.guidance)
    }

    @Test
    fun bundlesTwelveCompleteCapabilityPacks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val retriever = AssetLocalKnowledgeRetriever(context)
        val summary = retriever.catalogSummary()

        assertEquals(12, summary.packCount)
        assertTrue(summary.entryCount >= 60)
        assertEquals(
            setOf(
                "电话与通讯录", "微信与社交", "医疗健康", "出行", "购物服务", "金融防诈",
                "政务", "娱乐学习", "相机照片文档", "设备与无障碍", "居家与社区", "安全与恢复",
            ),
            summary.packNames,
        )
        assertTrue(summary.taskIds.containsAll(setOf("voicemail", "wearable_health_data", "payment_entry", "accessibility_settings")))
    }

    @Test
    fun returnsPackSafetyAndRecoveryMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val retriever = AssetLocalKnowledgeRetriever(context)

        val finance = retriever.retrieve("对方让我共享屏幕，是不是诈骗", "共享屏幕\n验证码")
        assertEquals("finance_fraud_warning", finance?.id)
        assertEquals("金融防诈", finance?.capabilityPack)
        assertEquals(LocalKnowledgeRiskLevel.GUIDANCE_ONLY, finance?.riskLevel)
        assertTrue(finance?.recoveryInstruction?.contains("不要提供密码") == true)

        val emergency = retriever.retrieve("我要打120急救电话", "紧急呼叫")
        assertEquals("emergency_call", emergency?.id)
        assertEquals(LocalKnowledgeRiskLevel.URGENT, emergency?.riskLevel)
    }
}
