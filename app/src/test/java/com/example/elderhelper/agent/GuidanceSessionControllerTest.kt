package com.example.elderhelper.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceSessionControllerTest {
    @Test
    fun repeatsTheCurrentInstructionWithoutCreatingANewModelRequest() {
        val controller = GuidanceSessionController()
        controller.begin("wifi_connect", "第一步：点击无线网络。")

        assertEquals(
            GuidanceSessionAction.Reply("第一步：点击无线网络。"),
            controller.handle("请再说一遍"),
        )
        assertEquals(GuidanceSessionState.WAITING_FOR_CONFIRMATION, controller.state)
    }

    @Test
    fun recoversOrFinishesTheActiveTaskLocally() {
        val controller = GuidanceSessionController()
        controller.begin(
            taskId = "wifi_connect",
            instruction = "第一步：点击无线网络。",
            recoveryInstruction = "请先回到设置首页，再找无线网络。",
        )

        assertEquals(
            GuidanceSessionAction.Reply("请先回到设置首页，再找无线网络。"),
            controller.handle("没找到"),
        )
        assertEquals(
            GuidanceSessionAction.Reply("好的，这一步已经完成。请继续说你接下来想做什么。"),
            controller.handle("好了"),
        )
        assertEquals(GuidanceSessionState.IDLE, controller.state)
        assertEquals(GuidanceSessionAction.NewRequest, controller.handle("帮我打开蓝牙"))
    }

    @Test
    fun cancellationClearsTheSession() {
        val controller = GuidanceSessionController()
        controller.begin("wifi_connect", "第一步：点击无线网络。")

        assertEquals(
            GuidanceSessionAction.Reply("好的，已经结束这次操作。"),
            controller.handle("算了"),
        )
        assertEquals(GuidanceSessionState.IDLE, controller.state)
    }
}
