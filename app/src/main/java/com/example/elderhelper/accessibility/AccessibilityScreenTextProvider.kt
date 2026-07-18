package com.example.elderhelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.elderhelper.agent.WorkflowTeachingHarness

/**
 * Optional, local-only screen text source. It keeps a short-lived text snapshot and never logs
 * it. The agent can answer simple questions from this snapshot without image-model inference.
 */
class AccessibilityScreenTextProvider : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        cacheCurrentWindowText(event?.packageName?.toString())
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source ?: return
            try {
                WorkflowTeachingHarness.recordClick(
                    packageName = event.packageName?.toString(),
                    viewId = source.viewIdResourceName,
                    visibleLabel = source.text?.toString() ?: source.contentDescription?.toString(),
                    isEditable = source.isEditable,
                )
            } finally {
                source.recycle()
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        cacheCurrentWindowText(eventPackageName = null)
    }

    override fun onDestroy() {
        ScreenTextSnapshotStore.clear()
        super.onDestroy()
    }

    private fun cacheCurrentWindowText(eventPackageName: String?) {
        val root = rootInActiveWindow ?: return
        try {
            val values = LinkedHashSet<String>()
            collectVisibleText(root, values, visitedNodes = intArrayOf())
            ScreenTextSnapshotStore.update(
                packageName = root.packageName?.toString() ?: eventPackageName,
                text = values.joinToString("\n"),
            )
        } finally {
            root.recycle()
        }
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        values: MutableSet<String>,
        visitedNodes: IntArray,
    ) {
        if (visitedNodes[0] >= MAX_NODES) return
        visitedNodes[0] += 1
        if (node.isVisibleToUser && !node.isPassword) {
            node.text?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(values::add)
            node.contentDescription?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(values::add)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectVisibleText(child, values, visitedNodes)
            } finally {
                child.recycle()
            }
        }
    }

    private companion object {
        private const val MAX_NODES = 120
    }
}

object ScreenTextSnapshotStore {
    @Volatile private var latest: TimedScreenContext? = null

    fun snapshot(): ForegroundScreenContext? {
        val value = latest ?: return null
        return value
            .takeIf { System.currentTimeMillis() - it.updatedAtMs <= MAX_AGE_MS }
            ?.toPublicContext()
    }

    internal fun update(packageName: String?, text: String) {
        latest = text.takeIf(String::isNotBlank)?.let {
            TimedScreenContext(packageName, it, System.currentTimeMillis())
        }
    }

    internal fun clear() {
        latest = null
    }

    private data class TimedScreenContext(
        val packageName: String?,
        val text: String,
        val updatedAtMs: Long,
    ) {
        fun toPublicContext(): ForegroundScreenContext = ForegroundScreenContext(packageName, text)
    }

    private const val MAX_AGE_MS = 10_000L
}

data class ForegroundScreenContext(
    val packageName: String?,
    val screenText: String,
)
