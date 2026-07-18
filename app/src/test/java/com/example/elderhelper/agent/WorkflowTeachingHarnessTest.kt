package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowTeachingHarnessTest {
    @Test
    fun capturesOnlySafeSemanticStepsForAnExplicitLowRiskTeachingSession() {
        WorkflowTeachingHarness.start()
        WorkflowTeachingHarness.setGoalFromUserQuestion("教我微信发照片")
        WorkflowTeachingHarness.recordClick("com.tencent.mm", "com.tencent.mm:id/bq", "加号", isEditable = false)
        WorkflowTeachingHarness.recordClick("com.tencent.mm", "com.tencent.mm:id/album", "相册", isEditable = false)

        val result = WorkflowTeachingHarness.finish()

        assertTrue(result is TeachingFinish.Draft)
        val draft = (result as TeachingFinish.Draft).workflow
        assertEquals(LearnedWorkflowIntent.WECHAT_SEND_PHOTO, draft.intent)
        assertEquals(2, draft.steps.size)
        assertTrue(draft.reviewText().contains("点击“相册”"))
        assertFalse(draft.reviewText().contains("教我微信发照片"))
    }

    @Test
    fun discardsHighRiskTeachingImmediately() {
        WorkflowTeachingHarness.start()
        WorkflowTeachingHarness.setGoalFromUserQuestion("教我怎么付款")
        WorkflowTeachingHarness.recordClick(
            "com.eg.android.AlipayGphone",
            "com.eg.android.AlipayGphone:id/pay",
            "付款",
            isEditable = false,
        )

        val result = WorkflowTeachingHarness.finish()

        assertTrue(result is TeachingFinish.Blocked)
        assertFalse(WorkflowTeachingHarness.isActive())
    }

    @Test
    fun ignoresEditableControlsInsteadOfRecordingTypedInformation() {
        WorkflowTeachingHarness.start()
        WorkflowTeachingHarness.setGoalFromUserQuestion("教我微信发照片")
        WorkflowTeachingHarness.recordClick("com.tencent.mm", "com.tencent.mm:id/input", "秘密内容", isEditable = true)

        assertTrue(WorkflowTeachingHarness.finish() is TeachingFinish.Empty)
    }
}
