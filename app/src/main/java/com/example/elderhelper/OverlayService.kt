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
import android.Manifest
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.asr.SpeechConstant

import org.json.JSONObject
import org.json.JSONException


// --- Mock AI Analyzer (Inside or outside OverlayService class) ---
interface AICallback {
    fun onResponse(guidance: String)
}

class AIAnalyzer {
    private val apiKey = BuildConfig.GEMINI_API_KEY // Get Gemini key
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    // Initialize the GenerativeModel
    private val generativeModel: GenerativeModel? = if (apiKey.isNotEmpty() && apiKey != "YOUR_API_KEY") { // Added check for placeholder
        try {
            GenerativeModel(
                modelName = "gemini-1.5-flash", // Using 1.5 flash, ensure this is intended
                apiKey = apiKey,
                 safetySettings = listOf(
                     SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                     SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
                     SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                     SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
                 ),
                 generationConfig = GenerationConfig.Builder().apply {
                     temperature = 0.7f
                     topK = 1
                     topP = 1f
                     maxOutputTokens = 1024
                 }.build()
            )
         } catch (e: Exception) {
             Log.e(TAG, "Error initializing GenerativeModel: ${e.message}", e)
             null // Initialization failed
         }
    } else {
        Log.e(TAG, "GEMINI_API_KEY is empty or placeholder. AI Analyzer disabled.")
        null
    }

    fun analyzeScreenAndQuery(screenBitmap: Bitmap?, query: String, callback: AICallback) {
        if (generativeModel == null) {
             Log.e(TAG, "GenerativeModel not initialized (API key missing, placeholder, or init failed?). Cannot analyze.")
             CoroutineScope(Dispatchers.Main).launch {
                 callback.onResponse("抱歉，AI服务未正确配置。")
             }
             return
        }
        Log.d(TAG, "Sending query and image (present: ${screenBitmap != null}) to Gemini API: '$query'")
        coroutineScope.launch {
            try {
                val inputContent = content {
                    if (screenBitmap != null) {
                        this.image(screenBitmap)
                        this.text("这是当前的屏幕截图。用户的语音问题是：$query")
                    } else {
                        this.text("用户的语音问题是：$query")
                    }
                }
                 val response = generativeModel.generateContent(inputContent)
                val responseText = response.text?.trim()

                if (responseText != null) {
                    Log.i(TAG, "Gemini API response: $responseText")
                    withContext(Dispatchers.Main) {
                        callback.onResponse(responseText)
                    }
                } else {
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
                val errorMessage = when(e) {
                    is ServerException -> "AI服务器错误，请稍后重试。(${e.message})"
                    is UnsupportedUserLocationException -> "当前地区不支持此服务。"
                    is UnknownHostException -> "网络连接错误，无法访问AI服务器。"
                    is CancellationException -> "请求被取消了。"
                    is ResponseStoppedException -> "AI回复因安全原因或其他限制被终止。(${e.response.promptFeedback?.blockReason})"
                    is SerializationException -> "解析AI回复时出错。"
                    is PromptBlockedException -> "请求因安全原因被阻止。(${e.response.promptFeedback?.blockReason})"
                    else -> "调用AI服务时发生未知错误。(${e::class.simpleName})"
                }
                withContext(Dispatchers.Main) {
                    callback.onResponse(errorMessage)
                }
            } finally {
                 // Recycle bitmap here after Gemini call is complete
                 // Ensure it's done even on errors or null responses
                 // Move recycle from sendToAI to here? No, sendToAI calls this async.
                 // Recycling should happen after callback.onResponse is called.
                 // Let's keep recycling within sendToAI's callback for now.
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

class OverlayService : Service(), TextToSpeech.OnInitListener, EventListener {

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
    private var wasDragged = false

    // --- ADD Baidu ASR Variables ---
    private var asrEventManager: EventManager? = null
    private var isRecording: Boolean = false // Keep this flag for UI state
    private var isEngineReadyForNext: Boolean = true // <<< ADD New flag

    // TextToSpeech Variables
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false

    // AI Analyzer Variable
    private lateinit var aiAnalyzer: AIAnalyzer

    // --- ADD variable to store last partial result ---
    private var lastPartialResult: String? = null
    // --- End add variable ---

     // Screen Capture Variables
     private var screenWidth: Int = 0
     private var screenHeight: Int = 0
     private var screenDensity: Int = 0
     private var imageReader: ImageReader? = null
     private var virtualDisplay: VirtualDisplay? = null
     private var imageListener: OnImageAvailableListener? = null
     @Volatile private var capturedBitmapForAnalysis: Bitmap? = null
     private val isQueryPending = AtomicBoolean(false) // Keep for screen capture coordination

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "ElderHelperOverlayChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.example.elderhelper.ACTION_STOP_SERVICE"
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.elderhelper.ACTION_MEDIA_PROJECTION_RESULT" // Keep for MainActivity
        const val EXTRA_RESULT_CODE = "resultCode" // Keep for MainActivity
        const val EXTRA_RESULT_DATA = "resultData" // Keep for MainActivity
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Initialize components
        initializeBaiduASR() // Initialize Baidu ASR
        initializeTextToSpeech()
        aiAnalyzer = AIAnalyzer()
        getScreenDimensions()

        startForegroundServiceNotification()
        createOverlayWindow()
        setupImageListener()
    }

    // --- Initialize Baidu ASR ---
    private fun initializeBaiduASR() {
        // Use EventManagerFactory create an instance of EventManager
        asrEventManager = EventManagerFactory.create(this, "asr")
        // Register the EventListener
        asrEventManager?.registerListener(this) // 'this' implements EventListener
        Log.i(TAG, "Baidu ASR EventManager initialized and listener registered.")
        // Load credentials here once? Or pass them in start parameters? Let's pass in start.
    }
    // --- End Baidu ASR Init ---

    // Initialize TextToSpeech
    private fun initializeTextToSpeech() {
         try {
            textToSpeech = TextToSpeech(this, this)
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
             overlayView = inflater.inflate(R.layout.overlay_layout, null)
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

    // Setup touch listener for dragging and press-and-hold speech
     private fun setupTouchListener(view: View) {
         val overlayButton = view.findViewById<ImageView>(R.id.overlay_button)

         // --- Click Listener for Start/Stop Recording --- //
         overlayButton?.setOnClickListener {
             if (wasDragged) {
                 wasDragged = false // Reset flag and ignore click after drag
                 Log.d(TAG,"Click ignored after drag.")
                 return@setOnClickListener
             }

             if (asrEventManager == null) {
                  Log.e(TAG, "Baidu ASR EventManager is null. Cannot proceed.")
                  Toast.makeText(this, "语音识别服务未初始化", Toast.LENGTH_SHORT).show()
                  return@setOnClickListener
             }

             if (!isRecording && isEngineReadyForNext) { // <<< MODIFIED: Add check for isEngineReadyForNext
                 // --- Start Recording ---
                 if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                     if (!isQueryPending.get()) { // Check if another process is ongoing
                         Log.i(TAG, "Click: Starting recording & capture request.")
                         isRecording = true // Set recording state immediately
                         isEngineReadyForNext = false // <<< ADD: Set engine busy flag
                         overlayButton.alpha = 0.5f // Indicate recording visually

                         // --- Prepare Baidu start parameters ---
                         val params = JSONObject()
                         try {
                             // Use String Literals for Authentication Keys
                             params.put(SpeechConstant.APP_ID, BuildConfig.BAIDU_APP_ID)
                             params.put(SpeechConstant.APP_KEY, BuildConfig.BAIDU_API_KEY)
                             params.put(SpeechConstant.SECRET, BuildConfig.BAIDU_SECRET_KEY)
                             // Keep using constants for other params where they exist
                             params.put(SpeechConstant.PID, 1537)
                             params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false)
                             params.put(SpeechConstant.VAD_ENDPOINT_TIMEOUT, 0) // Set to 0 to disable VAD for testing
                         } catch (e: JSONException) {
                             Log.e(TAG, "Error creating Baidu ASR start JSON parameters", e)
                             Toast.makeText(this, "配置语音识别参数失败", Toast.LENGTH_SHORT).show()
                             isRecording = false
                             overlayButton.alpha = 1.0f
                             return@setOnClickListener
                         }

                         // --- Start screen capture request ---
                          capturedBitmapForAnalysis?.recycle()
                          capturedBitmapForAnalysis = null
                          requestScreenCapture() // Set screen capture pending flag

                         // --- Send start command to Baidu SDK ---
                         val jsonParamString = params.toString()
                         Log.d(TAG, "Sending Baidu ASR start command: $jsonParamString")
                         asrEventManager?.send(SpeechConstant.ASR_START, jsonParamString, null, 0, 0)

                     } else {
                         Log.w(TAG, "Click: Ignored start, query still pending (screen capture).")
                         Toast.makeText(this, "正在处理上一条...", Toast.LENGTH_SHORT).show()
                     }
                 } else {
                     Log.e(TAG, "Click: Ignored start, RECORD_AUDIO permission missing.")
                     Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
                 }
             } else if (isRecording) {
                 // --- Stop Baidu Recording ---
                 Log.i(TAG, "Click: Stopping Baidu recording.")
                 isRecording = false // Set recording state immediately
                 overlayButton.alpha = 0.8f // Indicate processing visually (optional)

                 // --- Send stop command to Baidu SDK ---
                 asrEventManager?.send(SpeechConstant.ASR_STOP, null, null, 0, 0)
                 Toast.makeText(this, "正在处理...", Toast.LENGTH_SHORT).show()
             } else if (!isEngineReadyForNext) { // <<< ADD: Handle case where engine is not ready
                 Log.w(TAG, "Click: Ignored start, ASR engine not ready yet.")
                 Toast.makeText(this, "请稍候，引擎正在准备...", Toast.LENGTH_SHORT).show()
             }
         }

         // --- Touch Listener primarily for Dragging --- //
         overlayButton?.setOnTouchListener { _, event ->
              val x = event.rawX
              val y = event.rawY
              val dragThreshold = 15f // Pixel threshold to consider it a drag

              when (event.action) {
                  MotionEvent.ACTION_DOWN -> {
                      initialX = params.x
                      initialY = params.y
                      initialTouchX = x
                      initialTouchY = y
                      wasDragged = false // Reset drag flag on new touch sequence
                      return@setOnTouchListener false // Don't consume DOWN, let MOVE/UP/Click handle it
                  }
                  MotionEvent.ACTION_MOVE -> {
                      val dx = x - initialTouchX
                      val dy = y - initialTouchY

                      if (!wasDragged && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                          wasDragged = true // Set drag flag
                          Log.d(TAG, "Drag detected during touch.")
                          // If recording is active, update visual state to indicate drag is happening?
                          // Maybe reset alpha to normal during drag?
                          overlayButton.alpha = 1.0f
                      }

                      if (wasDragged) {
                           params.x = initialX + dx.toInt()
                           params.y = initialY + dy.toInt()
                           try {
                               windowManager.updateViewLayout(overlayView, params)
                           } catch (e: Exception) {
                               Log.e(TAG, "Error updating overlay position: ${e.message}")
                           }
                      }

                      // Consume MOVE event if dragging occurred to prevent other listeners.
                     return@setOnTouchListener wasDragged
                  }
                  MotionEvent.ACTION_UP -> {
                      // If ACTION_UP occurs, check if a drag happened.
                      // If it was a drag (wasDragged is true), consume the event (return true)
                      // to prevent the OnClickListener from firing.
                      // Otherwise (wasDragged is false), don't consume (return false), allowing
                      // the OnClickListener to register this as a click.
                      val consumeEvent = wasDragged
                       // Reset button appearance if dragging just ended (alpha might be 1.0 from MOVE)
                       // If it was NOT a drag, the click listener will handle alpha changes.
                      // if (consumeEvent) {
                      //     overlayButton.alpha = 1.0f // Reset alpha if drag ended
                      // }
                      return@setOnTouchListener consumeEvent
                  }
                   MotionEvent.ACTION_CANCEL -> {
                      Log.w(TAG, "ACTION_CANCEL received.")
                      wasDragged = false // Reset drag flag
                      overlayButton.alpha = 1.0f // Ensure button is visually reset
                      // If recording was active, should CANCEL cancel it? Maybe.
                      // Let's add cancellation logic here for safety, similar to error handling.
                       if (isRecording) {
                           Log.w(TAG, "ACTION_CANCEL received while recording, cancelling Baidu ASR.")
                           asrEventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
                           isRecording = false
                           isQueryPending.set(false)
                       }
                      return@setOnTouchListener false // Don't consume CANCEL typically
                  }
              }
              // Default: don't consume the event (return false)
              false
          }
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
            // Maybe stop Baidu ASR if it's running?
            if (isRecording) {
                 Log.w(TAG, "MediaProjection stopped, cancelling Baidu ASR.")
                 asrEventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
                 isRecording = false
            }
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
        isQueryPending.set(false)
        if (overlayView != null) { try { windowManager.removeView(overlayView); overlayView = null; Log.d(TAG, "Overlay view removed.") } catch (e: Exception) { Log.e(TAG, "Error removing overlay view: ${e.message}") } }
        try { mediaProjection?.unregisterCallback(MediaProjectionCallback()) } catch (e:Exception) {}
        try { mediaProjection?.stop(); Log.d(TAG, "MediaProjection stopped.") } catch (e: Exception) { Log.e(TAG, "Error stopping media projection: ${e.message}") }
        mediaProjection = null
        releaseScreenCaptureComponents()
        stopForeground(true)
        Log.d(TAG, "Service stopped foreground state.")

        // --- Release Baidu ASR Resources ---
        asrEventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0) // Cancel any ongoing process
        asrEventManager?.unregisterListener(this) // Unregister listener
        asrEventManager = null // Release reference
        Log.d(TAG, "Baidu ASR EventManager unregistered and released.")

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

        capturedBitmapForAnalysis?.recycle()
        capturedBitmapForAnalysis = null

        aiAnalyzer.cancelJobs()

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
        // This service is started, not bound. Return null.
        return null
    }

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

    // --- Implement Baidu EventListener Method --- //
    // Make sure the signature matches the Baidu EventListener interface exactly
    override fun onEvent(name: String?, params: String?, data: ByteArray?, offset: Int, length: Int) {
         val eventTime = System.currentTimeMillis()
         var logMessage = "Baidu ASR event: name=$name"
         if (params != null && params.isNotEmpty()) logMessage += ", params=$params"

         when (name) {
             SpeechConstant.CALLBACK_EVENT_ASR_READY -> {
                 // Engine ready, can start speaking
                 Log.d(TAG, logMessage)
                 handler.post { Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show() }
             }
             SpeechConstant.CALLBACK_EVENT_ASR_BEGIN -> {
                 // Detected user start speaking
                 Log.d(TAG, logMessage)
                 // handler.post { Toast.makeText(this, "检测到说话", Toast.LENGTH_SHORT).show() } // Optional
             }
             SpeechConstant.CALLBACK_EVENT_ASR_END -> {
                 // Detected user stop speaking
                 Log.d(TAG, logMessage)
                 // handler.post { Toast.makeText(this, "检测到语音结束", Toast.LENGTH_SHORT).show() } // Optional
             }
             SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL -> {
                 // Partial recognition results
                 // Log.v(TAG, logMessage) // Can be verbose
                 val result = parseResult(params)
                 if (result != null) {
                      // Maybe update UI with partial result? For now, just log.
                      val currentPartial = result.bestResult // Assuming parseResult gives partial here too
                      if (!currentPartial.isNullOrBlank()) {
                          Log.d(TAG, "Partial result: $currentPartial")
                          // --- Store the latest non-empty partial result ---
                          lastPartialResult = currentPartial
                          // --- End store ---
                      }
                 }
             }
             SpeechConstant.CALLBACK_EVENT_ASR_FINISH -> {
                 // Final recognition results (even if empty)
                 Log.i(TAG, logMessage)
                 val result = parseResult(params) // Parse finish event (might contain error info)
                 // --- Get the best result from the stored last partial result ---
                 val bestResult = lastPartialResult
                 lastPartialResult = null // Clear after use
                 // --- End get best result ---

                 // --- Reset state ---
                 // isRecording should have been set to false on click/stop already
                 // Or should we reset it here based on ASR_EXIT? Let's reset here on finish/error too for safety.
                 isRecording = false
                 isEngineReadyForNext = true // <<< ADD: Set engine ready flag
                 handler.post { overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f }

                 // --- Check if the finish event indicates success (error code 0) ---
                 val isSuccess = (result?.errorCode == 0 || (result?.errorCode == -1 && bestResult != null)) // Check error code from finish event or if we have a partial result

                 if (isSuccess && bestResult != null && bestResult.isNotEmpty()) {
                 // --- End check ---
                     Log.i(TAG, "Final Result (from last partial): $bestResult")
                     // Retrieve captured bitmap and send to AI
                      val bitmapToSend = capturedBitmapForAnalysis
                      capturedBitmapForAnalysis = null // Clear after getting reference

                     if (bitmapToSend != null) {
                         Log.i(TAG, "Bitmap found, sending text and bitmap to AI.")
                         sendToAI(bitmapToSend, bestResult)
                     } else {
                         Log.w(TAG, "Bitmap not available when SR results arrived. Sending text only.")
                          sendToAI(null, bestResult)
                         // isQueryPending should have been reset by the capture listener or error
                     }
                 }
                 // --- Use the error code from the FINISH event for error reporting ---
                 else if (result?.errorCode != 0 && result?.errorCode != -1) {
                      Log.w(TAG, "Baidu ASR Error on Finish: ${result?.errorDetail} (${result?.errorCode})")
                      isQueryPending.set(false)
                      capturedBitmapForAnalysis?.recycle()
                      capturedBitmapForAnalysis = null
                      val errorMsg = result?.errorDetail ?: "未能识别语音"
                      handler.post { Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show() }
                 } else {
                 // --- Fallback if no error but also no partial result was stored ---
                      Log.w(TAG, "Baidu ASR: No final result available (success event but no prior partial result).")
                 // --- End fallback ---
                      isQueryPending.set(false)
                      capturedBitmapForAnalysis?.recycle() // Clean up bitmap if ASR failed
                      capturedBitmapForAnalysis = null
                      // Show error based on result object?
                     // val errorMsg = if (result?.errorDetail != null) result.errorDetail else "未能识别语音" // Don't show toast if success but no result
                     // handler.post { Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show() }
                 }
             }
             SpeechConstant.CALLBACK_EVENT_ASR_EXIT -> {
                 // Recognizer exited (called after ASR_FINISH or ASR_ERROR)
                 Log.d(TAG, logMessage)
                 // Final state reset confirmation
                  isRecording = false
                  isEngineReadyForNext = true // <<< ADD: Set engine ready flag
                  handler.post { overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f }
                  // isQueryPending should be false now due to finish/error handling above
             }
             SpeechConstant.CALLBACK_EVENT_ASR_ERROR -> {
                 // Recognition error
                 Log.e(TAG, logMessage) // Log full params for error details
                 val result = parseResult(params) // Try to parse error details
                 val errorCode = result?.errorCode ?: -1
                 val errorDetail = result?.errorDetail ?: "未知语音错误"
                 lastPartialResult = null // Clear any stored partial result on error

                 // --- Reset state ---
                 isRecording = false
                 isQueryPending.set(false) // Reset capture flag on error
                 isEngineReadyForNext = true // <<< ADD: Set engine ready flag on error too
                 handler.post {
                     overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f
                     Toast.makeText(this, "语音识别错误: $errorDetail ($errorCode)", Toast.LENGTH_LONG).show()
                 }
                 capturedBitmapForAnalysis?.recycle()
                 capturedBitmapForAnalysis = null
             }
             SpeechConstant.CALLBACK_EVENT_ASR_CANCEL -> {
                  Log.w(TAG, logMessage)
                  lastPartialResult = null // Clear any stored partial result on cancel
                  // Reset state if cancelled externally (e.g., by drag)
                  isRecording = false
                  isQueryPending.set(false)
                  isEngineReadyForNext = true // <<< ADD: Set engine ready flag on cancel
                  handler.post { overlayView?.findViewById<ImageView>(R.id.overlay_button)?.alpha = 1.0f }
                  capturedBitmapForAnalysis?.recycle()
                  capturedBitmapForAnalysis = null
             }
             // Handle other events if needed (e.g., VOLUME, LONG_SPEECH)
             else -> {
                 // Log unhandled events
                 // Log.v(TAG, logMessage)
             }
         }
    }

     // Helper to parse Baidu ASR results/errors from JSON params
     private data class BaiduAsrResult(
         val bestResult: String?,
         val resultsRecognition: List<String>?,
         val errorCode: Int?,
         val errorDetail: String?,
         val subErrorCode: Int?,
         val desc: String?
     )

    private fun parseResult(jsonParams: String?): BaiduAsrResult? {
        if (jsonParams == null || jsonParams.isEmpty()) return null
        try {
            val json = JSONObject(jsonParams)

             val bestResult = json.optJSONArray("results_recognition")?.optString(0)
             val resultsRecognition = json.optJSONArray("results_recognition")?.let { arr ->
                 (0 until arr.length()).map { arr.getString(it) }
             }

             // Prefer err_no if available, otherwise use error
             var errorCode = json.optInt("err_no", -1)
             if (errorCode == -1) { // Fallback to "error" field if "err_no" not present or -1
                 errorCode = json.optInt("error", -1)
             }
             var errorDetail = json.optString("desc", null)
             var subErrorCode = json.optInt("sub_error", -1)

             // Sometimes error details are in 'origin_result' for ASR_FINISH on error
             if (json.has("origin_result")) {
                 try {
                      val originResult = JSONObject(json.getString("origin_result"))
                      if (originResult.has("err_no") && originResult.getInt("err_no") != 0) {
                          errorCode = originResult.optInt("err_no", errorCode)
                          errorDetail = originResult.optString("err_msg", errorDetail)
                      }
                 } catch (e: JSONException) { /* Ignore inner parse error */ }
             }

             // Check for standard error fields if not found in origin_result
             if (errorCode == -1 && json.has("errorNum")) errorCode = json.getInt("errorNum") // Alternate names?
             if (errorDetail == null && json.has("errorDetail")) errorDetail = json.getString("errorDetail")

             // If desc is generic success message but we have a more specific error, keep errorDetail
             if (errorDetail != null && errorDetail != "Speech Recognize success.") {
                  // Keep the more specific error detail
             } else if (errorDetail != null && errorDetail != "Speech Recognize success.") {
                 // Already have errorDetail, no need to re-assign if it's not the success message
                 // errorDetail = errorDetail // This line is redundant, can be removed or kept for clarity
             }

             // The 'desc' field in the BaiduAsrResult data class was intended to hold the final description, which is errorDetail here.
             return BaiduAsrResult(bestResult, resultsRecognition, errorCode, errorDetail, subErrorCode, errorDetail)

        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing Baidu ASR JSON parameters: $jsonParams", e)
            return null
        }
    }
    // --- End Baidu EventListener ---
} 