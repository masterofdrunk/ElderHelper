package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyResiliencePlannerTest {
    @Test
    fun prioritizesEmergencyAndScamInterruptionWithoutModelInference() {
        val emergency = SafetyResiliencePlanner.plan("有人晕倒了，快救命", null, null)
        val scam = SafetyResiliencePlanner.plan(
            "客服让我开启屏幕共享退款",
            null,
            "com.eg.android.AlipayGphone",
        )

        assertEquals("emergency_call", emergency?.capabilityId)
        assertTrue(emergency?.message?.contains("120") == true)
        assertEquals("scam_interruption", scam?.capabilityId)
        assertTrue(scam?.message?.contains("不要转账") == true)
        assertTrue(scam?.message?.contains("支付宝") == true)
    }

    @Test
    fun handlesRecoveryAndFamilyHandoffLocally() {
        assertEquals(
            "lost_phone_recovery",
            SafetyResiliencePlanner.plan("我的手机丢了", null, null)?.capabilityId,
        )
        assertEquals(
            "family_handoff",
            SafetyResiliencePlanner.plan("我想找家人帮忙", null, null)?.capabilityId,
        )
    }
}
