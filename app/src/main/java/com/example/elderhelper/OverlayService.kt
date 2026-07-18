package com.example.elderhelper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.elderhelper.analyzer.LocalFirstScreenAnalyzer
import com.example.elderhelper.analyzer.AssetLocalKnowledgeRetriever
import com.example.elderhelper.analyzer.ScreenAnalyzer
import com.example.elderhelper.analyzer.ScreenGuidanceAgent
import com.example.elderhelper.analyzer.ScreenGuidancePlan
import com.example.elderhelper.accessibility.ScreenTextSnapshotStore
import com.example.elderhelper.agent.GuidanceSessionAction
import com.example.elderhelper.agent.GuidanceSessionController
import com.example.elderhelper.agent.LearnedWorkflowIntent
import com.example.elderhelper.agent.LearnedWorkflowStore
import com.example.elderhelper.agent.WorkflowTeachingHarness
import com.example.elderhelper.privacy.SensitiveOperationGuard
import com.example.elderhelper.screen.MediaProjectionScreenCaptureProvider
import com.example.elderhelper.screen.ScreenCaptureProvider
import com.example.elderhelper.speech.SherpaOnnxSttEngine
import com.example.elderhelper.speech.SpeechToTextEngine
import com.example.elderhelper.tts.AndroidTtsSpeaker
import com.example.elderhelper.tts.SpeechSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var speechEngine: SpeechToTextEngine
    private lateinit var speaker: SpeechSpeaker
    private lateinit var screenAnalyzer: ScreenAnalyzer
    private val screenGuidanceAgent by lazy { ScreenGuidanceAgent(AssetLocalKnowledgeRetriever(applicationContext)) }
    private val sensitiveOperationGuard = SensitiveOperationGuard()
    private val guidanceSession = GuidanceSessionController()
    private val learnedWorkflowStore by lazy { LearnedWorkflowStore(applicationContext) }

    private var screenCaptureProvider: ScreenCaptureProvider? = null
    private var overlayView: View? = null
    private var overlayStatusText: TextView? = null
    private var overlayReplayText: TextView? = null
    private var overlayMessageClearRunnable: Runnable? = null
    private var lastGuidance: String? = null
    private var isProcessing = false
    private var wasDragged = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        speechEngine = SherpaOnnxSttEngine(this)
        speaker = AndroidTtsSpeaker(this)
        screenAnalyzer = LocalFirstScreenAnalyzer(this)

        startAsForeground()
        createOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == MainActivity.ACTION_MEDIA_PROJECTION_RESULT) {
            val resultCode = intent.getIntExtra(MainActivity.EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(MainActivity.EXTRA_RESULT_DATA)
            if (resultCode != 0 && resultData != null) {
                screenCaptureProvider?.release()
                screenCaptureProvider = MediaProjectionScreenCaptureProvider(this, resultCode, resultData)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayWindow()
        screenCaptureProvider?.release()
        speechEngine.release()
        speaker.release()
        screenAnalyzer.release()
        guidanceSession.clear()
        lastGuidance = null
        serviceScope.cancel()
    }

    private fun createOverlayWindow() {
        if (overlayView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        val overlayButton = view.findViewById<ImageView>(R.id.overlay_button)
        overlayStatusText = view.findViewById(R.id.overlay_status_text)
        overlayReplayText = view.findViewById(R.id.overlay_replay_text)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        overlayButton.setOnClickListener {
            if (wasDragged) {
                wasDragged = false
                return@setOnClickListener
            }
            handleOverlayClick(overlayButton)
        }
        overlayButton.setOnTouchListener { _, event ->
            handleOverlayDrag(event)
        }
        overlayReplayText?.setOnClickListener {
            val guidance = lastGuidance ?: return@setOnClickListener
            speaker.speak(guidance)
            showOverlayMessage(guidance, OVERLAY_MESSAGE_LONG_MS)
        }

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window.", e)
            stopSelf()
        }
    }

    private fun handleOverlayClick(overlayButton: ImageView) {
        if (isProcessing) {
            showOverlayMessage("正在处理上一条，请稍等")
            return
        }

        if (speechEngine.isRecording) {
            stopRecordingAndAnalyze(overlayButton)
        } else {
            startRecording(overlayButton)
        }
    }

    private fun startRecording(overlayButton: ImageView) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showOverlayMessage("需要录音权限")
            return
        }

        try {
            speechEngine.startListening()
            overlayButton.alpha = RECORDING_ALPHA
            showOverlayMessage("请说话，再点一次结束")
            speaker.speak("请说话。说完以后，再点一次按钮。")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local STT.", e)
            showOverlayMessage("无法开始录音")
        }
    }

    private fun stopRecordingAndAnalyze(overlayButton: ImageView) {
        isProcessing = true
        overlayButton.alpha = PROCESSING_ALPHA
        showOverlayMessage("正在处理")

        serviceScope.launch {
            val sttResult = speechEngine.stopAndTranscribe()
            if (!sttResult.isSuccess) {
                finishInteraction(
                    overlayButton = overlayButton,
                    message = sttResult.errorMessage ?: "没有听清楚，请再说一次。",
                )
                return@launch
            }
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Local STT recognized ${sttResult.text.orEmpty().length} chars.")
            }
            showOverlayMessage("已识别语音，正在分析屏幕")

            val userQuestion = sttResult.text.orEmpty()
            when (val sessionAction = guidanceSession.handle(userQuestion)) {
                is GuidanceSessionAction.Reply -> {
                    Log.i(TAG, "Handled a guidance-session command locally.")
                    finishInteraction(overlayButton, sessionAction.message)
                    return@launch
                }
                GuidanceSessionAction.NewRequest -> Unit
            }

            val foregroundScreen = ScreenTextSnapshotStore.snapshot()
            val screenText = foregroundScreen?.screenText
            if (WorkflowTeachingHarness.isActive()) {
                WorkflowTeachingHarness.setGoalFromUserQuestion(userQuestion)
            } else {
                val learnedIntent = LearnedWorkflowIntent.fromQuestion(userQuestion)
                learnedWorkflowStore.find(learnedIntent, foregroundScreen?.packageName)?.let { workflow ->
                    val guidance = workflow.replayGuidance()
                    guidanceSession.begin(taskId = "learned:${learnedIntent.name}", instruction = guidance)
                    Log.i(TAG, "Replayed an approved learned workflow locally.")
                    finishInteraction(overlayButton, guidance)
                    return@launch
                }
            }
            val sensitiveWarning = sensitiveOperationGuard.warningFor(userQuestion, screenText)
            when (
                val plan = screenGuidanceAgent.plan(
                    userQuestion = userQuestion,
                    screenText = screenText,
                    sensitiveWarning = sensitiveWarning,
                    foregroundPackage = foregroundScreen?.packageName,
                )
            ) {
                is ScreenGuidancePlan.LocalAnswer -> {
                    Log.i(TAG, "Screen guidance answered locally before screenshot capture.")
                    plan.taskId?.let { taskId ->
                        guidanceSession.begin(
                            taskId = taskId,
                            instruction = plan.guidance,
                            recoveryInstruction = plan.recoveryInstruction
                                ?: "没关系。请先回到刚才的页面，再说一说屏幕上能看到的文字；我会继续带你找。",
                        )
                    }
                    finishInteraction(overlayButton, plan.guidance)
                    return@launch
                }
                ScreenGuidancePlan.NeedsVisionModel -> Unit
            }

            showOverlayMessage("正在看屏幕")
            val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                screenCaptureProvider?.capture()
            }
            val analyzerResult = withContext(Dispatchers.Default) {
                screenAnalyzer.analyze(
                    screenBitmap = bitmap,
                    screenText = screenText,
                    userQuestion = userQuestion,
                )
            }
            bitmap?.recycle()

            val message = if (analyzerResult.guidance.isNotBlank()) {
                analyzerResult.guidance
            } else {
                analyzerResult.errorMessage ?: "暂时无法理解当前屏幕，请稍后再试。"
            }
            if (analyzerResult.guidance.isNotBlank() && !analyzerResult.isSensitive) {
                guidanceSession.begin(taskId = "vision-guidance", instruction = message)
            }
            finishInteraction(overlayButton, message)
        }
    }

    private fun finishInteraction(overlayButton: ImageView, message: String) {
        isProcessing = false
        overlayButton.alpha = IDLE_ALPHA
        lastGuidance = message
        speaker.speak(message)
        mainHandler.post {
            overlayReplayText?.visibility = View.VISIBLE
        }
        showOverlayMessage(message, OVERLAY_MESSAGE_LONG_MS)
    }

    private fun showOverlayMessage(message: String, durationMs: Long = OVERLAY_MESSAGE_SHORT_MS) {
        mainHandler.post {
            val statusText = overlayStatusText ?: return@post
            overlayMessageClearRunnable?.let(mainHandler::removeCallbacks)
            statusText.text = message
            statusText.visibility = View.VISIBLE
            overlayMessageClearRunnable = Runnable {
                if (overlayStatusText == statusText && statusText.text == message) {
                    statusText.visibility = View.GONE
                }
            }
            mainHandler.postDelayed(overlayMessageClearRunnable!!, durationMs)
        }
    }

    private fun handleOverlayDrag(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                wasDragged = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(deltaX) > DRAG_THRESHOLD || kotlin.math.abs(deltaY) > DRAG_THRESHOLD) {
                    wasDragged = true
                    layoutParams.x = (initialX + deltaX).coerceIn(EDGE_MARGIN_PX, maxOverlayX())
                    layoutParams.y = (initialY + deltaY).coerceIn(EDGE_MARGIN_PX, maxOverlayY())
                    overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (wasDragged) {
                    // Keep the control easy to find. A draggable overlay must never be left
                    // entirely outside the display or hidden under the navigation area.
                    layoutParams.x = if (layoutParams.x < resources.displayMetrics.widthPixels / 2) {
                        EDGE_MARGIN_PX
                    } else {
                        maxOverlayX()
                    }
                    layoutParams.y = layoutParams.y.coerceIn(EDGE_MARGIN_PX, maxOverlayY())
                    overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
                    mainHandler.postDelayed({ wasDragged = false }, DRAG_CLICK_RESET_DELAY_MS)
                    return true
                }
            }
        }
        return false
    }

    private fun maxOverlayX(): Int = (resources.displayMetrics.widthPixels - overlayWidthPx() - EDGE_MARGIN_PX)
        .coerceAtLeast(EDGE_MARGIN_PX)

    private fun maxOverlayY(): Int = (resources.displayMetrics.heightPixels - overlayHeightPx() - NAVIGATION_SAFE_AREA_PX)
        .coerceAtLeast(EDGE_MARGIN_PX)

    private fun overlayWidthPx(): Int = overlayView?.width?.takeIf { it > 0 } ?: overlayButtonSizePx()

    private fun overlayHeightPx(): Int = overlayView?.height?.takeIf { it > 0 } ?: overlayButtonSizePx()

    private fun overlayButtonSizePx(): Int = (58 * resources.displayMetrics.density).toInt()

    private fun removeOverlayWindow() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (ignored: Exception) {
        }
        overlayView = null
        overlayStatusText = null
        overlayMessageClearRunnable?.let(mainHandler::removeCallbacks)
        overlayMessageClearRunnable = null
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ElderHelper",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_action_text), stopPendingIntent)
            .build()
    }

    private fun startAsForeground() {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundServiceType,
        )
    }

    private companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "elderhelper_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.example.elderhelper.ACTION_STOP_SERVICE"
        private const val IDLE_ALPHA = 1.0f
        private const val RECORDING_ALPHA = 0.5f
        private const val PROCESSING_ALPHA = 0.75f
        private const val DRAG_THRESHOLD = 15
        private const val DRAG_CLICK_RESET_DELAY_MS = 100L
        private const val EDGE_MARGIN_PX = 16
        private const val NAVIGATION_SAFE_AREA_PX = 120
        private const val CAPTURE_TIMEOUT_MS = 2_000L
        private const val OVERLAY_MESSAGE_SHORT_MS = 2_000L
        private const val OVERLAY_MESSAGE_LONG_MS = 4_000L
    }
}
