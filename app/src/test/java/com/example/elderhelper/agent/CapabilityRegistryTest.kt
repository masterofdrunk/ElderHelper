package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun givesEachSupportedTaskAStableCategoryAndRecoveryPath() {
        val wechatPhoto = CapabilityRegistry.find("wechat_send_photo")

        assertEquals("家庭与社交", wechatPhoto?.category)
        assertEquals("wechat", wechatPhoto?.preferredPlaybookId)
        assertTrue(wechatPhoto?.recoveryInstruction?.contains("聊天页面") == true)
        assertTrue(CapabilityRegistry.isLearningAllowed("wechat_send_photo"))
    }

    @Test
    fun neverAllowsLearningEmergencyGuidance() {
        assertEquals(CapabilityRisk.URGENT, CapabilityRegistry.find("emergency_call")?.riskLevel)
        assertFalse(CapabilityRegistry.isLearningAllowed("emergency_call"))
    }
}
