package com.example.elderhelper.agent

/**
 * Keeps only the current guidance turn in memory. It never persists speech, screenshots, or
 * screen text, so restarting the service intentionally starts a fresh private session.
 */
internal class GuidanceSessionController {
    var state: GuidanceSessionState = GuidanceSessionState.IDLE
        private set

    private var activeTask: ActiveGuidanceTask? = null

    fun begin(
        taskId: String,
        instruction: String,
        recoveryInstruction: String = DEFAULT_RECOVERY,
    ) {
        activeTask = ActiveGuidanceTask(taskId, instruction, recoveryInstruction)
        state = GuidanceSessionState.WAITING_FOR_CONFIRMATION
    }

    fun handle(userText: String): GuidanceSessionAction {
        val task = activeTask ?: return GuidanceSessionAction.NewRequest
        return when (classify(userText)) {
            SessionUtterance.REPEAT -> GuidanceSessionAction.Reply(task.instruction)
            SessionUtterance.NOT_FOUND -> GuidanceSessionAction.Reply(task.recoveryInstruction)
            SessionUtterance.COMPLETE -> {
                finish()
                GuidanceSessionAction.Reply("好的，这一步已经完成。请继续说你接下来想做什么。")
            }
            SessionUtterance.CANCEL -> {
                finish()
                GuidanceSessionAction.Reply("好的，已经结束这次操作。")
            }
            SessionUtterance.NEW_REQUEST -> GuidanceSessionAction.NewRequest
        }
    }

    fun clear() {
        activeTask = null
        state = GuidanceSessionState.IDLE
    }

    private fun finish() {
        state = GuidanceSessionState.FINISHED
        activeTask = null
        state = GuidanceSessionState.IDLE
    }

    private fun classify(userText: String): SessionUtterance {
        val normalized = userText
            .lowercase()
            .replace(Regex("[\\s，。！？、,.!?]"), "")
        return when {
            repeatPhrases.any(normalized::contains) -> SessionUtterance.REPEAT
            notFoundPhrases.any(normalized::contains) -> SessionUtterance.NOT_FOUND
            completePhrases.any(normalized::contains) -> SessionUtterance.COMPLETE
            cancelPhrases.any(normalized::contains) -> SessionUtterance.CANCEL
            else -> SessionUtterance.NEW_REQUEST
        }
    }

    private data class ActiveGuidanceTask(
        val id: String,
        val instruction: String,
        val recoveryInstruction: String,
    )

    private enum class SessionUtterance {
        REPEAT,
        NOT_FOUND,
        COMPLETE,
        CANCEL,
        NEW_REQUEST,
    }

    private companion object {
        const val DEFAULT_RECOVERY =
            "没关系。请先回到刚才的页面，再说一说屏幕上能看到的文字；我会继续带你找。"
        val repeatPhrases = listOf("再说一遍", "再说一次", "重复一下", "没听清", "听不清")
        val notFoundPhrases = listOf("没找到", "找不到", "没有看到", "不在这里", "不是这个")
        val completePhrases = listOf("好了", "完成了", "找到了", "可以了", "已经好了")
        val cancelPhrases = listOf("停止", "结束", "取消", "算了", "不用了", "返回")
    }
}

internal enum class GuidanceSessionState {
    IDLE,
    GUIDING,
    WAITING_FOR_CONFIRMATION,
    RECOVERING,
    FINISHED,
}

internal sealed interface GuidanceSessionAction {
    data object NewRequest : GuidanceSessionAction

    data class Reply(val message: String) : GuidanceSessionAction
}
