package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityServicePlannerTest {
    @Test
    fun guidesMealServiceWithoutPlacingAnOrder() {
        val plan = CommunityServicePlanner.plan("我想找附近助餐服务")

        assertEquals("community_service_entry", plan?.capabilityId)
        assertTrue(plan?.guidance?.contains("提交订单前") == true)
    }

    @Test
    fun keepsRepairRequestsAwayFromPrivateDepositsAndCodes() {
        val plan = CommunityServicePlanner.plan("家里水管坏了，想找维修")

        assertEquals("community_service_entry", plan?.capabilityId)
        assertTrue(plan?.guidance?.contains("验证码") == true)
    }
}
