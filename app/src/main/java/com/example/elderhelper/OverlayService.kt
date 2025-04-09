package com.example.elderhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.app.Activity.RESULT_OK
import com.example.elderhelper.R
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Locale
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import java.nio.ByteBuffer
import android.media.ImageReader.OnImageAvailableListener
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.net.UnknownHostException
import com.example.elderhelper.BuildConfig
//import com.google.ai.client.generativeai.type.InvalidApiKeyException
//import com.google.ai.client.generativeai.type.GenerativeAiException


// --- Mock AI Analyzer (Inside or outside OverlayService class) ---
interface AICallback {
    fun onResponse(guidance: String)
}

class AIAnalyzer {
    private val apiKey = BuildConfig.GEMINI_API_KEY // Get Gemini key
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    // Initialize the GenerativeModel
    private val generativeModel: GenerativeModel? = if (apiKey.isNotEmpty()) {
        try {
            // For text-and-image input (multimodal), use gemini-1.5-flash-latest as gemini-pro-vision is deprecated
            GenerativeModel(
                modelName = "gemini-1.5-flash-latest", // Use the latest flash model supporting vision
                apiKey = apiKey,
                // Optional: Add safety settings and generation config
                 safetySettings = listOf(
                     SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                     SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
                     SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                     SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
                 ),
                 generationConfig = GenerationConfig.Builder().apply {
                     temperature = 0.7f // Example temperature
                     topK = 1
                     topP = 1f
                     maxOutputTokens = 1024 // Increase limit
                 }.build()
            )
         } catch (e: Exception) {
             Log.e(TAG, "Error initializing GenerativeModel: ${e.message}", e)
             null // Initialization failed
         }
    } else {
        Log.e(TAG, "GEMINI_API_KEY is empty. AI Analyzer disabled.")
        null
    }

    fun analyzeScreenAndQuery(screenBitmap: Bitmap?, query: String, callback: AICallback) {
        if (generativeModel == null) {
             Log.e(TAG, "GenerativeModel not initialized (API key missing or init failed?). Cannot analyze.")
             CoroutineScope(Dispatchers.Main).launch {
                 callback.onResponse("抱歉，AI服务未正确配置。")
             }
             return
        }

        Log.d(TAG, "Sending query and image (present: ${screenBitmap != null}) to Gemini API: '$query'")
        coroutineScope.launch {
            try {
                // --- Construct the multimodal input using content builder --- //
                val inputContent = content {
                    if (screenBitmap != null) {
                        this.image(screenBitmap)
                        this.text("这是当前的屏幕截图。用户的语音问题是：$query")
                    } else {
                        this.text("用户的语音问题是：$query")
                    }
                    // Role defaults to 'user'
                }

                // --- Call the API --- //
                 val response = generativeModel.generateContent(inputContent)

                // Process the response
                val responseText = response.text?.trim()

                if (responseText != null) {
                    Log.i(TAG, "Gemini API response: $responseText")
                    withContext(Dispatchers.Main) {
                        callback.onResponse(responseText)
                    }
                } else {
                    // Log detailed reasons for null response
                    Log.w(TAG, "Gemini API returned null or empty text content.")
                    val blockReason = response.promptFeedback?.blockReason?.toString() ?: "未知原因"
                    val finishReason = response.candidates?.firstOrNull()?.finishReason?.toString() ?: "未知"
                    val safetyRatings = response.promptFeedback?.safetyRatings?.joinToString { "${it.category}: ${it.probability}" } ?: "N/A"
                    Log.w(TAG, "BlockReason: $blockReason, FinishReason: $finishReason, SafetyRatings: $safetyRatings")

                    withContext(Dispatchers.Main) {
                        callback.onResponse("抱歉，AI未能生成有效的回复。原因: $finishReason")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
                // Provide specific error messages
                val errorMessage = when(e) {
                   // is InvalidApiKeyException -> "API密钥无效或过期。"
                    is ServerException -> "AI服务器错误，请稍后重试。(${e.message})"
                    is UnsupportedUserLocationException -> "当前地区不支持此服务。"
                    is UnknownHostException -> "网络连接错误，无法访问AI服务器。"
                    is CancellationException -> "请求被取消了。"
                    // Catch other potential Gemini exceptions if needed
                    is ResponseStoppedException -> "AI回复因安全原因或其他限制被终止。(${e.response.promptFeedback?.blockReason})"
                    is SerializationException -> "解析AI回复时出错。"
                    is PromptBlockedException -> "请求因安全原因被阻止。(${e.response.promptFeedback?.blockReason})"
                   // is GenerativeAiException -> "调用AI服务时出错: ${e.message}"
                    else -> "调用AI服务时发生未知错误。(${e::class.simpleName})"
                }
                withContext(Dispatchers.Main) {
                    callback.onResponse(errorMessage)
                }
            }
        }
    }

     fun cancelJobs() {
        Log.d(TAG, "Cancelling AIAnalyzer jobs.")
        job.cancel()
    }

     companion object {
         private const val TAG = "AIAnalyzer"
     }
}
// --- End AI Analyzer ---

class OverlayService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionResultCode: Int = 0
    private var mediaProjectionData: Intent? = null

    // For moving the overlay window
    private lateinit var params: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var isLongClick = false
    private var isDragging = false // More specific flag for dragging state
    private val longClickDuration = 500L // milliseconds for long press

    // Speech Recognition Variables
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording: Boolean = false
    private lateinit var speechRecognizerIntent: Intent

    // TextToSpeech Variables
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false

    // AI Analyzer Variable
    private lateinit var aiAnalyzer: AIAnalyzer

     // Screen Capture Variables
     private var screenWidth: Int = 0
     private var screenHeight: Int = 0
     private var screenDensity: Int = 0

    // Screen Capture Components (now more persistent)
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageListener: OnImageAvailableListener? = null // Keep listener reference

    // Callback for when capture is complete
    @Volatile private var capturedBitmapForAnalysis: Bitmap? = null // Store captured bitmap
    private var latestUserQuery: String? = null // Keep for passing text

    // --- NEW: Pending Query Flag ---
    private val isQueryPending = AtomicBoolean(false)

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "ElderHelperOverlayChannel"
        private const val NOTIFICATION_ID = 1
        // Action for stopping the service via notification
        const val ACTION_STOP_SERVICE = "com.example.elderhelper.ACTION_STOP_SERVICE"
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Initialize components
        initializeSpeechRecognizer()
        initializeTextToSpeech()
        aiAnalyzer = AIAnalyzer() // Initialize our new AIAnalyzer
        getScreenDimensions() // Get screen size info

        // Service needs to be foreground BEFORE adding the overlay window on newer Android versions
        startForegroundServiceNotification()
        createOverlayWindow()
        setupImageListener() // Setup listener logic once
    }

    // Initialize Speech Recognizer
    private fun initializeSpeechRecognizer() {
         if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available on this device.")
            Toast.makeText(this, "此设备不支持语音识别", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this) // Set listener to this service

            speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN") // Use standard IETF tag
            }
            Log.i(TAG, "SpeechRecognizer initialized successfully for Chinese (zh-CN).")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}", e)
            Toast.makeText(this, "初始化语音识别失败", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    // Initialize TextToSpeech
    private fun initializeTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(this, this) // Set listener to this service
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
             Toast.makeText(this, "初始化语音播报失败", Toast.LENGTH_SHORT).show()
        }
    }

    // Get screen dimensions needed for screen capture
    private fun getScreenDimensions() {
        val metrics = DisplayMetrics()
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
             try {
                 val display = display
                 display?.getRealMetrics(metrics)
             } catch (e: Exception) {
                  Log.w(TAG, "Could not get display using API 30 method, falling back.", e)
                  windowManager.defaultDisplay.getRealMetrics(metrics)
             }
         } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
         }
        if (metrics.widthPixels == 0 || metrics.heightPixels == 0) {
            Log.w(TAG, "Display metrics are zero, using resources fallback.")
            val displayMetrics = resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
        } else {
             screenWidth = metrics.widthPixels
             screenHeight = metrics.heightPixels
             screenDensity = metrics.densityDpi
        }
        Log.i(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}, Density: $screenDensity")
    }

    // Create and show the overlay window
    private fun createOverlayWindow() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay view already exists.")
            return
        }

        val inflater = LayoutInflater.from(this)
        try {
             overlayView = inflater.inflate(R.layout.overlay_layout, null) // Ensure layout exists
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating overlay layout R.layout.overlay_layout: ${e.message}", e)
            stopSelf() // Stop service if layout fails
            return
        }

         val overlayButton: ImageView? = try {
             overlayView?.findViewById(R.id.overlay_button) // Ensure ID exists
         } catch (e: Exception) {
             Log.e(TAG, "Error finding overlay button R.id.overlay_button: ${e.message}", e)
             stopSelf()
             return
         }

         val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            // Allow touches outside, non-focusable, fit screen
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100 // Initial X
        params.y = 300 // Initial Y

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay view added")
             overlayView?.let { setupTouchListener(it) } // Setup touch listener
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}", e)
            stopSelf()
        }
    }

    // Setup touch listener for dragging the overlay
     private fun setupTouchListener(view: View) {
         val overlayButton = view.findViewById<ImageView>(R.id.overlay_button)
         val longPressRunnable = Runnable {
             if (!isDragging) { // Only trigger long press if not already dragging from movement
                 isLongClick = true
                 isDragging = true // Enter dragging mode on long press
                 // Provide feedback if needed (vibration, visual change)
                 Log.d(TAG, "Long press detected - entering drag mode")
             }
         }

         overlayButton?.setOnTouchListener { _, event ->
             val x = event.rawX
             val y = event.rawY
             var handled = false // Flag if event was consumed

             when (event.action) {
                 MotionEvent.ACTION_DOWN -> {
                     initialX = params.x
                     initialY = params.y
                     initialTouchX = x
                     initialTouchY = y
                     isLongClick = false
                     isDragging = false // Reset dragging state on new touch down
                     handler.postDelayed(longPressRunnable, longClickDuration)
                     Log.v(TAG, "ACTION_DOWN: initialX=$initialX, initialY=$initialY, touchX=$initialTouchX, touchY=$initialTouchY")
                     handled = true
                 }
                 MotionEvent.ACTION_MOVE -> {
                     val dx = x - initialTouchX
                     val dy = y - initialTouchY
                      // Check significant movement
                     val significantMove = Math.abs(dx) > 10 || Math.abs(dy) > 10

                     if (significantMove && !isDragging) {
                         // If significant movement happens before long press, cancel long press and start dragging
                         handler.removeCallbacks(longPressRunnable)
                         isDragging = true
                         Log.d(TAG, "Movement detected - entering drag mode")
                     }

                     if (isDragging) {
                         params.x = initialX + dx.toInt()
                         params.y = initialY + dy.toInt()
                         Log.v(TAG, "ACTION_MOVE: Dragging to x=${params.x}, y=${params.y}")
                         try {
                             windowManager.updateViewLayout(overlayView, params)
                         } catch (e: Exception) {
                             Log.e(TAG, "Error updating overlay position: ${e.message}")
                         }
                         handled = true // Consume move events while dragging
                     }
                 }
                 MotionEvent.ACTION_UP -> {
                    Log.v(TAG, "ACTION_UP: isDragging=$isDragging, isLongClick=$isLongClick")
                    handler.removeCallbacks(longPressRunnable) // Always remove callbacks on UP

                    val wasDragging = isDragging // Capture state before reset

                    // Reset flags for next interaction
                    isDragging = false
                    isLongClick = false

                    if (!wasDragging) {
                        // *** If it wasn't a drag, treat it as a click ***
                        Log.d(TAG, "Tap detected in OnTouchListener ACTION_UP")
                        handleOverlayClick() // Directly call click handler
                        handled = true // Consume the event, as we handled the click
                    } else {
                        // If it was a drag, just consume the UP event
                        handled = true
                    }
                 }
                  MotionEvent.ACTION_CANCEL -> {
                     Log.d(TAG, "ACTION_CANCEL")
                     handler.removeCallbacks(longPressRunnable)
                     isLongClick = false
                     isDragging = false
                     handled = true
                 }
             }
              handled // Return true if event was handled/consumed
         }
     }


    // Handle Overlay Button Click - Modified for Tap-to-Talk
    private fun handleOverlayClick() {
        val overlayButton = overlayView?.findViewById<ImageView>(R.id.overlay_button)

        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer not initialized, cannot handle click.")
            Toast.makeText(this, "语音识别未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
             Log.e(TAG, "RECORD_AUDIO permission not granted!")
             Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
             stopSelf()
             return
        }

        // --- Only start if not already recording --- //
        if (!isRecording) {
            if (isQueryPending.get()) {
                // This check might be less critical now, but keep it for safety
                Log.w(TAG, "Query still pending from previous operation? Ignoring click.")
                 Toast.makeText(this, "正在处理上一条...", Toast.LENGTH_SHORT).show()
                return
            }

            Log.i(TAG, "Starting speech recognition AND screen capture request.")
            try {
                capturedBitmapForAnalysis?.recycle()
                capturedBitmapForAnalysis = null
                requestScreenCapture() // Set flag for listener to capture next image

                speechRecognizer?.startListening(speechRecognizerIntent)
                isRecording = true // Set recording state
                overlayButton?.alpha = 0.5f // Update button appearance
                // Toast.makeText(this, "正在聆听...", Toast.LENGTH_SHORT).show() // Replaced by onReadyForSpeech toast

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording/capture: ${e.message}", e)
                Toast.makeText(this, "启动录音或截屏失败", Toast.LENGTH_SHORT).show()
                // Reset states on failure
                isRecording = false
                isQueryPending.set(false)
                capturedBitmapForAnalysis?.recycle()
                capturedBitmapForAnalysis = null
                overlayButton?.alpha = 1.0f
            }
        } else {
            // --- If already recording, ignore the click --- //
            Log.d(TAG, "handleOverlayClick: Ignored click because already recording.")
             // Optionally provide feedback that recording is in progress
             // Toast.makeText(this, "正在录音中...", Toast.LENGTH_SHORT).show()
        }
        // --- REMOVED the old else block that called stopListening() --- //
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called, action: ${intent?.action}, startId: $startId")

        // Handle stopping the service from notification action
        if (intent?.action == ACTION_STOP_SERVICE) {
             Log.i(TAG, "Received stop service action. Stopping service.")
             stopSelf() // This will trigger onDestroy
             return START_NOT_STICKY // Don't restart after being explicitly stopped
        }

        if (intent?.action == MainActivity.ACTION_MEDIA_PROJECTION_RESULT) {
             if (mediaProjection == null) {
                 mediaProjectionResultCode = intent.getIntExtra(MainActivity.EXTRA_RESULT_CODE, 0)
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     mediaProjectionData = intent.getParcelableExtra(MainActivity.EXTRA_RESULT_DATA, Intent::class.java)
                 } else {
                    @Suppress("DEPRECATION")
                    mediaProjectionData = intent.getParcelableExtra(MainActivity.EXTRA_RESULT_DATA)
                 }

                if (mediaProjectionResultCode == RESULT_OK && mediaProjectionData != null) {
                     Log.i(TAG, "Received valid MediaProjection result data.")
                     try {
                         mediaProjection = mediaProjectionManager?.getMediaProjection(mediaProjectionResultCode, mediaProjectionData!!)
                         if (mediaProjection == null) {
                             Log.e(TAG, "Failed to get MediaProjection instance.")
                             stopSelf() // Stop if we can't get the projection
                         } else {
                             Log.i(TAG, "MediaProjection instance obtained successfully.")
                             mediaProjection?.registerCallback(MediaProjectionCallback(), handler) // Use member handler
                             // --- Create Persistent Capture Components --- //
                             createScreenCaptureComponents()
                         }
                     } catch (e: Exception) {
                         Log.e(TAG, "Exception getting MediaProjection: ${e.message}", e)
                         stopSelf()
                     }
                } else {
                    Log.e(TAG, "Received invalid MediaProjection result data (resultCode=$mediaProjectionResultCode, data null=${mediaProjectionData == null}). Stopping service.")
                    stopSelf()
                }
            }
        }

        // Ensure overlay is shown if not already
        createOverlayWindow()

        // START_STICKY: If the service is killed, restart it with a null intent.
        // START_REDELIVER_INTENT: If killed, restart with the last delivered intent.
        // We need the MediaProjection data, so START_REDELIVER_INTENT might seem appropriate,
        // but if MainActivity is gone, we can't get it again easily.
        // START_STICKY allows the service to restart and potentially wait for a new intent if needed,
        // or rely on the stored mediaProjection object if it survived.
        // Let's stick with START_STICKY and handle the null intent case gracefully.
        return START_STICKY
    }

     private inner class MediaProjectionCallback : MediaProjection.Callback() { // Inner class to access service members if needed
        override fun onStop() {
            Log.w(TAG, "MediaProjection stopped externally.")
            releaseScreenCaptureComponents() // Release VD/IR
            mediaProjection = null
            isQueryPending.set(false) // Reset flag
            Toast.makeText(this@OverlayService, "屏幕捕获已停止", Toast.LENGTH_SHORT).show()
        }
    }


     // Setup and start foreground notification
     private fun startForegroundServiceNotification() {
         val channelName = "Overlay Service Notification"
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW) // Use LOW importance
            chan.description = "Notification for the running ElderHelper overlay service"
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
         }

         // Intent to open MainActivity when notification is tapped
         val notificationIntent = Intent(this, MainActivity::class.java)
         val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

         // Intent for the stop action
         val stopServiceIntent = Intent(this, OverlayService::class.java).apply {
             action = ACTION_STOP_SERVICE
         }
         val stopServicePendingIntent = PendingIntent.getService(this, 0, stopServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

         // Use string resources for user-visible text
         val contentTitle = getString(R.string.app_name)
         val contentText = getString(R.string.overlay_notification_text)
         val tickerText = getString(R.string.overlay_ticker_text)

         val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            // Use a proper icon, ic_launcher_foreground might not be ideal for notification
            .setSmallIcon(R.drawable.ic_notification) // Replace with a dedicated notification icon
            .setContentIntent(pendingIntent) // Action when notification body is tapped
            .setTicker(tickerText)
             .setOngoing(true)
             .setPriority(NotificationCompat.PRIORITY_LOW) // Use LOW priority
             .setCategory(NotificationCompat.CATEGORY_SERVICE)
             // Add Stop action button
             .addAction(R.drawable.ic_stop, getString(R.string.stop_action_text), stopServicePendingIntent) // Use string resource
            .build()

        try {
             val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                 android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
             } else {
                 0 // No specific type needed before Q
             }

             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                 // Android 14 requires specifying allowed types again
                 startForeground(NOTIFICATION_ID, notification, serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
             } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  startForeground(NOTIFICATION_ID, notification, serviceType)
             } else {
                 startForeground(NOTIFICATION_ID, notification)
             }
             Log.d(TAG, "Started foreground service.")
         } catch (e: Exception) {
             Log.e(TAG, "Error starting foreground service: ${e.message}", e)
             stopSelf() // Stop if foreground fails
         }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        isQueryPending.set(false) // Ensure flag is reset on destroy
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null // Clear reference
                Log.d(TAG, "Overlay view removed.")
            } catch (e: Exception) {
                 Log.e(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        try {
            mediaProjection?.unregisterCallback(MediaProjectionCallback()) } catch (e:Exception) {} // Unregister callback too
        try {
            mediaProjection?.stop()
            Log.d(TAG, "MediaProjection stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection: ${e.message}")
        }
        mediaProjection = null // Clear reference
        releaseScreenCaptureComponents() // Release VD/IR
        // Stop foreground service state
        stopForeground(true)
        Log.d(TAG, "Service stopped foreground state.")

        // Release SpeechRecognizer
         try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            Log.d(TAG, "SpeechRecognizer destroyed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        }
        speechRecognizer = null

        // Release TextToSpeech
         try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            Log.d(TAG, "TextToSpeech shutdown.")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TextToSpeech: ${e.message}")
        }
        textToSpeech = null
        ttsReady = false

        // Clean up screen capture resources
        // releaseScreenCaptureComponents() // Already called above

        // TODO: Release AIAnalyzer resources here?

        // Recycle any leftover bitmap
        capturedBitmapForAnalysis?.recycle()
        capturedBitmapForAnalysis = null

        aiAnalyzer.cancelJobs() // Cancel any ongoing AI calls

        Log.d(TAG, "Service destroyed.")
    }

     // Handle configuration changes (like screen rotation) if needed
     override fun onConfigurationChanged(newConfig: Configuration) {
         super.onConfigurationChanged(newConfig)
         Log.d(TAG, "Configuration changed: ${newConfig.orientation}")
         // If the overlay position or size needs adjustment on rotation, do it here.
         // Example: Check new screen dimensions and update params.x/y if overlay goes off-screen.
     }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- RecognitionListener Methods ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "SpeechRecognizer: Ready for speech")
        Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show()
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "SpeechRecognizer: Beginning of speech")
    }

    override fun onRmsChanged(rmsdB: Float) { }

    override fun onBufferReceived(buffer: ByteArray?) { }

    override fun onEndOfSpeech() {
        Log.d(TAG, "SpeechRecognizer: End of speech")
    }

    override fun onError(error: Int) {
        val errorMessage = getSpeechErrorText(error)
        Log.e(TAG, "SR Error $error: $errorMessage")
        Toast.makeText(this, "语音识别错误: $errorMessage", Toast.LENGTH_SHORT).show()
        isRecording = false
        overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f
        isQueryPending.set(false) // Reset pending flag on SR error
        capturedBitmapForAnalysis?.recycle()
        capturedBitmapForAnalysis = null
    }

    override fun onResults(results: Bundle?) {
        Log.d(TAG, "SR onResults")
        isRecording = false
        overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null && matches.isNotEmpty()) {
            val userQuery = matches[0]
            Log.i(TAG, "Recognized: $userQuery")
            Toast.makeText(this, "识别结果: $userQuery", Toast.LENGTH_SHORT).show()

            val bitmapToSend = capturedBitmapForAnalysis
            capturedBitmapForAnalysis = null

            if (bitmapToSend != null) {
                 Log.i(TAG, "Bitmap found, sending text and bitmap to AI.")
                 sendToAI(bitmapToSend, userQuery)
            } else {
                 Log.w(TAG, "Bitmap not available when SR results arrived. Sending text only.")
                 sendToAI(null, userQuery)
                 // isQueryPending should have been reset by the listener or an error
            }
        } else {
             Log.w(TAG, "SR: No results."); Toast.makeText(this, "未能识别语音", Toast.LENGTH_SHORT).show()
             isQueryPending.set(false) // Reset pending flag if SR had no results
             capturedBitmapForAnalysis?.recycle()
             capturedBitmapForAnalysis = null
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        Log.d(TAG, "SpeechRecognizer: Partial results")
    }

    override fun onEvent(eventType: Int, params: Bundle?) { }

     // Helper function for readable speech errors
     private fun getSpeechErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "音频错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到结果"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙碌"
            SpeechRecognizer.ERROR_SERVER -> "服务端错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "无语音输入超时"
            else -> "未知语音错误 ($errorCode)"
        }
    }

    // --- End RecognitionListener Methods ---

    // --- NEW Async Screen Capture (stores result in member var) ---
    private fun requestScreenCapture() {
         Log.d(TAG, "Screen capture requested, setting pending flag.")
         isQueryPending.set(true)
         // No longer creates VD/IR here
    }

    // --- NEW Send data to AI --- //
    private fun sendToAI(bitmap: Bitmap?, query: String) {
        Log.i(TAG, "Sending to AI. Query: '$query', Bitmap present: ${bitmap != null}")
        Toast.makeText(this, "正在发送给AI...", Toast.LENGTH_SHORT).show()

        // AIAnalyzer now handles threading and API call
        aiAnalyzer.analyzeScreenAndQuery(bitmap, query, object : AICallback {
            override fun onResponse(guidance: String) {
                // This callback is already expected to be on the Main thread by AIAnalyzer
                Log.i(TAG, "AI Response received: $guidance")
                speakText(guidance)
                // Recycle bitmap *here* after AI processing is done (if it was used)
                 // Note: DeepSeek call currently ignores the bitmap, so recycling might happen sooner
                 // than actual analysis if DeepSeek had used it. For now, recycle anyway.
                 bitmap?.recycle()
                 Log.d(TAG, "Bitmap potentially recycled after AI analysis attempt.")
            }
        })
    }

    // --- TTS Helper ---
    private fun speakText(text: String?) {
        // --- ADD Preprocessing to remove asterisks --- //
        val cleanedText = text?.replace("*", "")?.trim() // Remove asterisks and trim whitespace

        if (!ttsReady || cleanedText.isNullOrBlank()) { // Check the cleaned text
            Log.w(TAG, "TTS not ready or cleaned text is empty/blank. Cannot speak. Original: '$text'")
            // Avoid showing toast for empty strings after cleaning
            // if (!cleanedText.isNullOrBlank()) Toast.makeText(this, "无法播报: $cleanedText", Toast.LENGTH_SHORT).show()
            return
        }
        if (textToSpeech == null) { Log.e(TAG, "textToSpeech instance is null!"); return }
        val utteranceId = this.hashCode().toString() + "" + System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // --- Speak the cleaned text --- //
            textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            // --- Speak the cleaned text --- //
            textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null)
        }
        Log.i(TAG, "TTS speaking: $cleanedText") // Log the cleaned text
    }
    // --- End TTS Helper ---

    // --- TextToSpeech OnInitListener ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Chinese language is not supported or missing data. Trying default.")
                val defaultResult = textToSpeech?.setLanguage(Locale.getDefault())
                 if (defaultResult == TextToSpeech.LANG_MISSING_DATA || defaultResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                     Log.e(TAG, "TTS: Default language also not supported.")
                     Toast.makeText(this, "语音播报不支持中文或默认语言", Toast.LENGTH_SHORT).show()
                     ttsReady = false
                 } else {
                      Log.w(TAG, "TTS initialized with default language.")
                      ttsReady = true
                 }
            } else {
                Log.i(TAG, "TTS initialized successfully with Chinese language.")
                ttsReady = true
            }
        } else {
            Log.e(TAG, "TTS Initialization failed! Status: $status")
            Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show()
            ttsReady = false
        }
        Log.i(TAG, "TTS ready: $ttsReady")
    }
    // --- End TextToSpeech OnInitListener ---

    // --- NEW: Create Persistent Capture Components ---
    private fun createScreenCaptureComponents() {
        if (mediaProjection == null || screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "Cannot create capture components: MP unavailable or invalid dims.")
            return
        }
        if (imageReader != null || virtualDisplay != null) {
            Log.w(TAG, "Capture components already exist. Skipping creation.")
            return
        }

        Log.d(TAG, "Creating persistent ImageReader and VirtualDisplay...")
        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2) // Increased maxImages slightly
            imageReader?.setOnImageAvailableListener(imageListener, handler) // Use pre-setup listener

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "PersistentCaptureVD", screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, handler
            )

            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create persistent VirtualDisplay.")
                imageReader?.close() // Clean up reader if VD fails
                imageReader = null
                stopSelf() // Cannot proceed without VD
            } else {
                Log.i(TAG, "Persistent ImageReader and VirtualDisplay created successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating persistent capture components: ${e.message}", e)
            releaseScreenCaptureComponents() // Clean up if anything failed
            stopSelf()
        }
    }

    // --- NEW: Release Persistent Capture Components ---
    private fun releaseScreenCaptureComponents() {
        Log.d(TAG, "Releasing persistent capture components...")
        try { virtualDisplay?.release() } catch (e: Exception) { Log.e(TAG, "Error releasing VD: ${e.message}") }
        try { imageReader?.close() } catch (e: Exception) { Log.e(TAG, "Error closing IR: ${e.message}") }
        virtualDisplay = null
        imageReader = null
        Log.d(TAG, "Persistent capture components released.")
    }

    // --- NEW: Setup Image Listener Logic ---
    private fun setupImageListener() {
        imageListener = OnImageAvailableListener { reader ->
            // Check if a query is waiting for an image
            if (!isQueryPending.get()) {
                var image: Image? = null
                try {
                    image = reader?.acquireLatestImage()
                    if (image != null) {
                        // Log.v(TAG, "Image available but no pending query, discarding.") // Can be verbose
                    }
                } catch (e: Exception) {
                    // Log infrequent errors if acquire/close fails unexpectedly
                    // Log.w(TAG, "Error acquiring/closing image when no query pending: ${e.message}")
                } finally {
                    try { image?.close() } catch (e: Exception) {}
                }
                return@OnImageAvailableListener // Exit early
            }

            // Try to consume the pending query flag
            if (isQueryPending.compareAndSet(true, false)) {
                Log.d(TAG, "Query pending, processing image...")
                var image: Image? = null
                var bitmap: Bitmap? = null
                try {
                    image = reader?.acquireLatestImage()
                    if (image != null) {
                        // --- Process the image --- //
                         val planes = image.planes; val buffer = planes[0].buffer; val pixelStride = planes[0].pixelStride; val rowStride = planes[0].rowStride; val rowPadding = rowStride - pixelStride * screenWidth; var tempBitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888); tempBitmap.copyPixelsFromBuffer(buffer); if (rowPadding > 0) { bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, screenWidth, screenHeight); tempBitmap.recycle() } else { bitmap = tempBitmap }
                        Log.i(TAG, "Bitmap created for pending query.")

                        // --- Store the result on the main thread --- //
                        handler.post {
                            capturedBitmapForAnalysis?.recycle() // Recycle old one
                            capturedBitmapForAnalysis = bitmap // Store new one
                            Log.i(TAG, "Captured bitmap stored for analysis.")
                        }
                    } else {
                        Log.w(TAG, "acquireLatestImage failed even though query was pending.")
                         handler.post { capturedBitmapForAnalysis = null } // Ensure null if acquire failed
                         // Should we reset isQueryPending here? Maybe not, let SR timeout handle it.
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image for pending query: ${e.message}", e)
                    bitmap?.recycle() // Recycle if created before error
                     handler.post { capturedBitmapForAnalysis = null } // Ensure null on error
                     // isQueryPending.set(false) // Reset on error?
                } finally {
                    try { image?.close() } catch (e: Exception) {} // Always close the image
                }
            } else {
                // Flag was true, but compareAndSet failed (race condition?) or flag became false again.
                // Just discard this image.
                var image: Image? = null
                 try {
                    image = reader?.acquireLatestImage()
                    // Log.v(TAG, "Query flag changed or race condition, discarding image.")
                } catch (e: Exception) {
                 } finally {
                    try { image?.close() } catch (e: Exception) {}
                }
            }
        }
    }
} 