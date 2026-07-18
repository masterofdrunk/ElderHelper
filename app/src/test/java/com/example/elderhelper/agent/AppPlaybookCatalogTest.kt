package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlaybookCatalogTest {
    @Test
    fun matchesKnownAppsAndKeepsUnknownPackagesOutOfThePlaybookPath() {
        val wechat = AppPlaybookCatalog.find("com.tencent.mm")

        assertEquals("wechat", wechat?.id)
        assertTrue(wechat?.supports("wechat_voice_video") == true)
        assertNull(AppPlaybookCatalog.find("com.example.unknown"))
    }

    @Test
    fun marksTransactionAppsAsHighRisk() {
        assertTrue(AppPlaybookCatalog.find("com.eg.android.AlipayGphone")?.highRisk == true)
        assertTrue(AppPlaybookCatalog.find("com.sankuai.meituan")?.highRisk == true)
    }
}
