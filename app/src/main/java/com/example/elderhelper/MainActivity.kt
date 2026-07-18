package com.example.elderhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.elderhelper.model.ModelDownloadController
import com.example.elderhelper.model.ModelDownloadService
import com.example.elderhelper.model.ModelDownloadStatus
import com.example.elderhelper.model.OfflineModelManager
import com.example.elderhelper.agent.LearnedWorkflowStore
import com.example.elderhelper.agent.TeachingFinish
import com.example.elderhelper.agent.WorkflowTeachingHarness
import com.example.elderhelper.accessibility.AccessibilityScreenTextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private val OVERLAY_PERMISSION_REQUEST_CODE = 102
    private val MODEL_NOTIFICATION_PERMISSION_CODE = 103
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var hasRecordAudioPermission = false
    private var hasOverlayPermission = false
    private var mediaProjectionIntent: Intent? = null
    private var mediaProjectionResultCode: Int = 0

    private lateinit var checkPermissionsButton: Button
    private lateinit var assistantReadinessText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var modelDownloadProgress: ProgressBar
    private lateinit var downloadModelsButton: Button
    private lateinit var cancelModelDownloadButton: Button
    private lateinit var deleteModelsButton: Button
    private lateinit var enableQuickScreenButton: Button
    private lateinit var startTeachingButton: Button
    private lateinit var finishTeachingButton: Button
    private lateinit var teachingStatusText: TextView
    private lateinit var quickScreenStatusText: TextView
    private lateinit var privacyButton: Button
    private lateinit var deleteLearnedWorkflowsButton: Button
    private lateinit var setupActionButton: Button
    private lateinit var setupReadinessText: TextView
    private lateinit var appBarTitle: TextView
    private lateinit var homeScreen: View
    private lateinit var offlineScreen: View
    private lateinit var setupScreen: View
    private lateinit var privacyScreen: View
    private lateinit var navHome: View
    private lateinit var navOffline: View
    private lateinit var navSetup: View
    private lateinit var navPrivacy: View
    private lateinit var navHomeIcon: ImageView
    private lateinit var navOfflineIcon: ImageView
    private lateinit var navSetupIcon: ImageView
    private lateinit var navPrivacyIcon: ImageView
    private lateinit var navHomeLabel: TextView
    private lateinit var navOfflineLabel: TextView
    private lateinit var navSetupLabel: TextView
    private lateinit var navPrivacyLabel: TextView
    private lateinit var learnedWorkflowStore: LearnedWorkflowStore
    private lateinit var offlineModelManager: OfflineModelManager

    companion object {
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.elderhelper.ACTION_MEDIA_PROJECTION_RESULT"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
    }

    private enum class MainSection {
        HOME,
        OFFLINE,
        SETUP,
        PRIVACY,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
             setContentView(R.layout.activity_main)
        } catch (e: IllegalStateException) {
             Log.e("MainActivity", "Theme Error setting content view. Ensure Theme.AppCompat descendant is used.", e)
             Toast.makeText(this, "应用主题配置错误，请联系开发者", Toast.LENGTH_LONG).show()
             finish()
             return
         } catch (e: Exception) {
             Log.e("MainActivity", "Error setting content view. Make sure R.layout.activity_main exists and project is built.", e)
             Toast.makeText(this, "无法加载界面布局", Toast.LENGTH_LONG).show()
             finish()
             return
        }

        try {
             checkPermissionsButton = findViewById(R.id.checkPermissionsButton)
             assistantReadinessText = findViewById(R.id.assistantReadinessText)
             modelStatusText = findViewById(R.id.modelStatusText)
             modelDownloadProgress = findViewById(R.id.modelDownloadProgress)
             downloadModelsButton = findViewById(R.id.downloadModelsButton)
             cancelModelDownloadButton = findViewById(R.id.cancelModelDownloadButton)
             deleteModelsButton = findViewById(R.id.deleteModelsButton)
             enableQuickScreenButton = findViewById(R.id.enableQuickScreenButton)
             startTeachingButton = findViewById(R.id.startTeachingButton)
             finishTeachingButton = findViewById(R.id.finishTeachingButton)
             teachingStatusText = findViewById(R.id.teachingStatusText)
             quickScreenStatusText = findViewById(R.id.quickScreenStatusText)
             privacyButton = findViewById(R.id.privacyButton)
             deleteLearnedWorkflowsButton = findViewById(R.id.deleteLearnedWorkflowsButton)
             setupActionButton = findViewById(R.id.setupActionButton)
             setupReadinessText = findViewById(R.id.setupReadinessText)
             appBarTitle = findViewById(R.id.appBarTitle)
             homeScreen = findViewById(R.id.homeScreen)
             offlineScreen = findViewById(R.id.offlineScreen)
             setupScreen = findViewById(R.id.setupScreen)
             privacyScreen = findViewById(R.id.privacyScreen)
             navHome = findViewById(R.id.navHome)
             navOffline = findViewById(R.id.navOffline)
             navSetup = findViewById(R.id.navSetup)
             navPrivacy = findViewById(R.id.navPrivacy)
             navHomeIcon = findViewById(R.id.navHomeIcon)
             navOfflineIcon = findViewById(R.id.navOfflineIcon)
             navSetupIcon = findViewById(R.id.navSetupIcon)
             navPrivacyIcon = findViewById(R.id.navPrivacyIcon)
             navHomeLabel = findViewById(R.id.navHomeLabel)
             navOfflineLabel = findViewById(R.id.navOfflineLabel)
             navSetupLabel = findViewById(R.id.navSetupLabel)
             navPrivacyLabel = findViewById(R.id.navPrivacyLabel)
        } catch (e: Exception) {
             Log.e("MainActivity", "Error finding button R.id.checkPermissionsButton. Check layout file and build.", e)
             Toast.makeText(this, "无法找到界面按钮", Toast.LENGTH_LONG).show()
             finish()
             return
        }

        offlineModelManager = OfflineModelManager(this)
        learnedWorkflowStore = LearnedWorkflowStore(this)
        downloadModelsButton.setOnClickListener { downloadOrUpdateOfflineModels() }
        cancelModelDownloadButton.setOnClickListener { ModelDownloadService.cancel(this) }
        deleteModelsButton.setOnClickListener { confirmDeleteOfflineModels() }
        enableQuickScreenButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, R.string.enable_quick_screen_hint, Toast.LENGTH_LONG).show()
        }
        startTeachingButton.setOnClickListener { startWorkflowTeaching() }
        finishTeachingButton.setOnClickListener { finishWorkflowTeaching() }
        privacyButton.setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        deleteLearnedWorkflowsButton.setOnClickListener { confirmClearLearnedWorkflows() }
        setupActionButton.setOnClickListener { checkAndRequestAllPermissions() }
        navHome.setOnClickListener { showSection(MainSection.HOME) }
        navOffline.setOnClickListener { showSection(MainSection.OFFLINE) }
        navSetup.setOnClickListener { showSection(MainSection.SETUP) }
        navPrivacy.setOnClickListener { showSection(MainSection.PRIVACY) }
        findViewById<View>(R.id.homePermissionsCard).setOnClickListener { showSection(MainSection.SETUP) }
        findViewById<View>(R.id.homeOfflineCard).setOnClickListener { showSection(MainSection.OFFLINE) }
        findViewById<View>(R.id.homeQuickScreenCard).setOnClickListener { showSection(MainSection.SETUP) }
        findViewById<View>(R.id.homePrivacyCard).setOnClickListener { showSection(MainSection.PRIVACY) }
        observeModelDownloadStatus()

        try {
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get MediaProjectionManager service", e)
            Toast.makeText(this, "无法访问屏幕捕获服务", Toast.LENGTH_LONG).show()
             checkPermissionsButton.isEnabled = false
             checkPermissionsButton.text = "屏幕捕获不可用"
             return
        }

        checkPermissionsButton.setOnClickListener {
            checkAndRequestAllPermissions()
        }

        checkPermissionsButton.text = "检查并请求权限"
        hasRecordAudioPermission = checkPermissionStatus(Manifest.permission.RECORD_AUDIO)
        hasOverlayPermission = checkOverlayPermissionStatus()
        refreshModelStatus()
        updateButtonState()
        updateTeachingButtons()
        refreshQuickScreenStatus()
        showSection(MainSection.HOME)

    }

    private fun showSection(section: MainSection) {
        homeScreen.visibility = if (section == MainSection.HOME) View.VISIBLE else View.GONE
        offlineScreen.visibility = if (section == MainSection.OFFLINE) View.VISIBLE else View.GONE
        setupScreen.visibility = if (section == MainSection.SETUP) View.VISIBLE else View.GONE
        privacyScreen.visibility = if (section == MainSection.PRIVACY) View.VISIBLE else View.GONE

        appBarTitle.text = when (section) {
            MainSection.HOME -> "ElderHelper"
            MainSection.OFFLINE -> "长者助手"
            MainSection.SETUP -> "ElderHelper"
            MainSection.PRIVACY -> "长者助手"
        }

        val activeColor = Color.WHITE
        val inactiveColor = Color.parseColor("#005F45")
        val inactiveTextColor = Color.parseColor("#3E4943")
        listOf(
            MainSection.HOME to Triple(navHome, navHomeIcon, navHomeLabel),
            MainSection.OFFLINE to Triple(navOffline, navOfflineIcon, navOfflineLabel),
            MainSection.SETUP to Triple(navSetup, navSetupIcon, navSetupLabel),
            MainSection.PRIVACY to Triple(navPrivacy, navPrivacyIcon, navPrivacyLabel),
        ).forEach { (itemSection, views) ->
            val isActive = itemSection == section
            views.first.setBackgroundResource(if (isActive) R.drawable.bg_nav_active else android.R.color.transparent)
            views.second.setColorFilter(if (isActive) activeColor else inactiveColor)
            views.third.setTextColor(if (isActive) activeColor else inactiveTextColor)
        }
    }

    private fun checkPermissionStatus(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startWorkflowTeaching() {
        WorkflowTeachingHarness.start()
        teachingStatusText.setText(R.string.teaching_started)
        startTeachingButton.isEnabled = false
        finishTeachingButton.isEnabled = true
    }

    private fun finishWorkflowTeaching() {
        when (val result = WorkflowTeachingHarness.finish()) {
            is TeachingFinish.Draft -> {
                AlertDialog.Builder(this)
                    .setTitle("检查学习到的操作")
                    .setMessage(result.workflow.reviewText())
                    .setPositiveButton(R.string.verify_and_save_workflow) { _, _ ->
                        learnedWorkflowStore.save(result.workflow)
                        teachingStatusText.setText(R.string.teaching_saved)
                        updateTeachingButtons()
                    }
                    .setNegativeButton(R.string.discard_workflow) { _, _ ->
                        WorkflowTeachingHarness.discard()
                        teachingStatusText.setText(R.string.teaching_idle)
                        updateTeachingButtons()
                    }
                    .show()
            }
            is TeachingFinish.Empty -> {
                teachingStatusText.text = result.message
                updateTeachingButtons()
            }
            is TeachingFinish.Blocked -> {
                teachingStatusText.text = result.message
                updateTeachingButtons()
            }
        }
    }

    private fun updateTeachingButtons() {
        val isTeaching = WorkflowTeachingHarness.isActive()
        startTeachingButton.isEnabled = !isTeaching
        finishTeachingButton.isEnabled = isTeaching
    }

    private fun refreshQuickScreenStatus() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                service.resolveInfo.serviceInfo.packageName == packageName &&
                    service.resolveInfo.serviceInfo.name == AccessibilityScreenTextProvider::class.java.name
            }
        quickScreenStatusText.setText(
            if (enabled) R.string.quick_screen_enabled else R.string.quick_screen_disabled,
        )
    }

    private fun confirmClearLearnedWorkflows() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_learned_workflows_title)
            .setMessage(R.string.delete_learned_workflows_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                learnedWorkflowStore.clearAll()
                Toast.makeText(this, R.string.learned_workflows_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

     private fun checkOverlayPermissionStatus(): Boolean {
         return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
             Settings.canDrawOverlays(this)
         } else {
             true
         }
    }

    private fun checkAndRequestAllPermissions() {
        if (!offlineModelManager.snapshot().complete) {
            Toast.makeText(this, R.string.models_required_before_start, Toast.LENGTH_LONG).show()
            return
        }
        val permissionsToRequest = mutableListOf<String>()

        if (!checkPermissionStatus(Manifest.permission.RECORD_AUDIO)) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        } else {
             hasRecordAudioPermission = true
        }

        if (!checkOverlayPermissionStatus()) {
             requestOverlayPermission()
        } else {
            hasOverlayPermission = true
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "Requesting standard permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), RECORD_AUDIO_PERMISSION_CODE)
        } else {
             if (hasRecordAudioPermission && hasOverlayPermission && mediaProjectionIntent == null) {
                 requestScreenCapturePermission()
             } else {
                  updateServiceState()
             }
        }
         updateButtonState()
    }

    private fun requestAudioPermission() {
        Log.d("MainActivity", "Requesting RECORD_AUDIO permission.")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "Requesting SYSTEM_ALERT_WINDOW permission via Intent.")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
        } else {
             Log.d("MainActivity", "Overlay permission already granted or not needed.")
             hasOverlayPermission = true
             checkServiceStartReadiness()
        }
    }

     private fun requestScreenCapturePermission() {
        if (mediaProjectionResultCode == RESULT_OK && mediaProjectionIntent != null) {
            Log.d("MainActivity", "Screen capture permission already granted in this session.")
            checkServiceStartReadiness()
            return
        }

        Log.d("MainActivity", "Requesting screen capture permission via intent...")
        try {
            if (!::mediaProjectionManager.isInitialized) {
                 Log.e("MainActivity", "MediaProjectionManager not initialized before requesting screen capture.")
                 Toast.makeText(this, "屏幕捕获服务初始化失败", Toast.LENGTH_SHORT).show()
                 return
            }
             startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
        } catch (e: Exception) {
             Log.e("MainActivity", "Error starting screen capture intent: ${e.message}", e)
             Toast.makeText(this, "无法启动屏幕捕获请求", Toast.LENGTH_SHORT).show()
             mediaProjectionIntent = null
             mediaProjectionResultCode = 0
             updateButtonState()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MODEL_NOTIFICATION_PERMISSION_CODE) {
            startOfflineModelDownloadService()
            return
        }
        var allStandardPermissionsGranted = true
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
             for (i in permissions.indices) {
                 when (permissions[i]) {
                     Manifest.permission.RECORD_AUDIO -> {
                         if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                             Log.i("MainActivity", "RECORD_AUDIO permission granted by user.")
                             hasRecordAudioPermission = true
                         } else {
                             Log.w("MainActivity", "RECORD_AUDIO permission denied by user.")
                             hasRecordAudioPermission = false
                             Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_LONG).show()
                             allStandardPermissionsGranted = false
                         }
                     }
                 }
             }
        }

        updateButtonState()
        if (allStandardPermissionsGranted && hasOverlayPermission) {
             checkServiceStartReadiness()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data null: ${data == null}")

        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                 hasOverlayPermission = checkOverlayPermissionStatus()
                  if (hasOverlayPermission) {
                      Log.i("MainActivity", "SYSTEM_ALERT_WINDOW permission granted by user (verified).")
                  } else {
                      Log.w("MainActivity", "SYSTEM_ALERT_WINDOW permission denied by user (verified).")
                      Toast.makeText(this, "需要悬浮窗权限才能显示助手", Toast.LENGTH_LONG).show()
                  }
                  updateButtonState()
                  checkServiceStartReadiness()
            }
            SCREEN_CAPTURE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    Log.i("MainActivity", "Screen capture permission granted by user.")
                     mediaProjectionResultCode = resultCode
                     mediaProjectionIntent = data.clone() as Intent
                    Log.d("MainActivity", "Stored MediaProjection result code and data intent.")
                } else {
                    Log.w("MainActivity", "Screen capture permission denied or cancelled by user (resultCode=$resultCode, data null:${data==null}).")
                    Toast.makeText(this, "需要屏幕捕获权限才能分析屏幕", Toast.LENGTH_SHORT).show()
                    mediaProjectionIntent = null
                    mediaProjectionResultCode = 0
                }
                 updateButtonState()
                 checkServiceStartReadiness()
            }
        }
    }

     private fun checkServiceStartReadiness() {
          if (hasAllPermissions()) {
              updateServiceState()
          } else {
               Log.d("MainActivity", "Not all permissions ready yet for service start.")
               if (hasRecordAudioPermission && hasOverlayPermission && mediaProjectionIntent == null) {
                    requestScreenCapturePermission()
               }
          }
     }

     private fun hasAllPermissions(): Boolean {
         val screenCaptureOk = mediaProjectionResultCode == RESULT_OK && mediaProjectionIntent != null
         val overlayOk = checkOverlayPermissionStatus()
         val audioOk = checkPermissionStatus(Manifest.permission.RECORD_AUDIO)

          Log.d("MainActivity", "hasAllPermissions check: Audio=$audioOk, Overlay=$overlayOk, ScreenCaptureResultValid=$screenCaptureOk")
         return audioOk && overlayOk && screenCaptureOk
    }

    private fun updateServiceState() {
        if (isServiceRunning(OverlayService::class.java)) {
            Log.d("MainActivity", "OverlayService is already running.")
            updateButtonState()
            return
        }

        if (hasAllPermissions()) {
            Log.i("MainActivity", "All permissions granted, attempting to start OverlayService.")
            val serviceIntent = Intent(this, OverlayService::class.java)

            if (mediaProjectionResultCode == RESULT_OK && mediaProjectionIntent != null) {
                 serviceIntent.action = ACTION_MEDIA_PROJECTION_RESULT
                 serviceIntent.putExtra(EXTRA_RESULT_CODE, mediaProjectionResultCode)
                 serviceIntent.putExtra(EXTRA_RESULT_DATA, mediaProjectionIntent)
                 Log.d("MainActivity", "Adding MediaProjection data to service intent.")

                 try {
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Toast.makeText(this, "助手服务已启动", Toast.LENGTH_SHORT).show()
                 } catch (e: Exception) {
                     Log.e("MainActivity", "Error starting OverlayService: ${e.message}", e)
                     Toast.makeText(this, "启动助手服务失败", Toast.LENGTH_SHORT).show()
                     mediaProjectionIntent = null
                     mediaProjectionResultCode = 0
                 }

            } else {
                 Log.e("MainActivity", "Cannot start service: MediaProjection data is missing or invalid!")
                 Toast.makeText(this, "无法启动服务：缺少屏幕捕获信息", Toast.LENGTH_LONG).show()
            }
            updateButtonState()

        } else {
            Log.w("MainActivity", "Not starting service: Not all permissions granted.")
            stopServiceIfRunning(OverlayService::class.java)
            stopServiceIfRunning(ScreenCaptureService::class.java)
            updateButtonState()
        }
    }

     private fun updateButtonState() {
          if (::offlineModelManager.isInitialized && !offlineModelManager.snapshot().complete) {
               checkPermissionsButton.text = getString(R.string.models_required_button)
               checkPermissionsButton.isEnabled = false
               assistantReadinessText.text = "请先下载离线包，完成后即可开启助手"
               syncSetupActionState()
               return
          }
          val serviceRunning = isServiceRunning(OverlayService::class.java)
          val allPermissionsGranted = hasAllPermissions()
          val missingPerms = mutableListOf<String>().apply {
              if (!checkPermissionStatus(Manifest.permission.RECORD_AUDIO)) add("录音")
              if (!checkOverlayPermissionStatus()) add("悬浮窗")
              if (mediaProjectionResultCode != RESULT_OK || mediaProjectionIntent == null) add("看屏")
          }

          if (serviceRunning) {
              checkPermissionsButton.text = "助手运行中"
              checkPermissionsButton.isEnabled = false
          } else if (allPermissionsGranted) {
               checkPermissionsButton.text = "启动助手服务"
               checkPermissionsButton.isEnabled = true
          } else {
              if (missingPerms.isEmpty()) {
                   checkPermissionsButton.text = "检查权限"
               } else {
                   checkPermissionsButton.text = "请求权限 (${missingPerms.joinToString()})"
               }
               checkPermissionsButton.isEnabled = true
          }

          assistantReadinessText.text = when {
              serviceRunning -> "助手正在运行，屏幕边缘有绿色圆形按钮"
              allPermissionsGranted -> "权限已准备好，点击下方按钮启动助手"
              else -> "还差：${missingPerms.joinToString("、")}；按下方按钮即可完成"
          }
          syncSetupActionState()
      }

     private fun syncSetupActionState() {
         if (!::setupActionButton.isInitialized) return
         setupActionButton.text = checkPermissionsButton.text
         setupActionButton.isEnabled = checkPermissionsButton.isEnabled
         setupReadinessText.text = assistantReadinessText.text
     }

    override fun onResume() {
        super.onResume()
        if (::offlineModelManager.isInitialized) {
            refreshModelStatus()
            updateButtonState()
            updateTeachingButtons()
            refreshQuickScreenStatus()
        }
    }

    private fun startOfflineModelDownload() {
        if (!offlineModelManager.hasNetworkConnection()) {
            Toast.makeText(this, R.string.model_download_network_required, Toast.LENGTH_LONG).show()
            return
        }
        if (!offlineModelManager.hasEnoughStorage()) {
            val required = OfflineModelManager.formatBytes(offlineModelManager.requiredFreeBytes())
            Toast.makeText(this, getString(R.string.model_download_storage_required, required), Toast.LENGTH_LONG).show()
            return
        }
        if (offlineModelManager.isActiveNetworkMetered()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.mobile_data_warning_title)
                .setMessage(R.string.mobile_data_warning_message)
                .setPositiveButton(R.string.continue_download) { _, _ -> requestNotificationAndStartDownload() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        requestNotificationAndStartDownload()
    }

    private fun downloadOrUpdateOfflineModels() {
        if (!offlineModelManager.snapshot().complete) {
            startOfflineModelDownload()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reinstall_offline_models_title)
            .setMessage(R.string.reinstall_offline_models_message)
            .setPositiveButton(R.string.reinstall_offline_models) { _, _ -> deleteOfflineModelsThenDownload() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteOfflineModels() {
        val usedBytes = OfflineModelManager.formatBytes(offlineModelManager.snapshot().installedBytes)
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_offline_models_title)
            .setMessage(getString(R.string.delete_offline_models_message, usedBytes))
            .setPositiveButton(R.string.delete) { _, _ -> deleteOfflineModels() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteOfflineModelsThenDownload() {
        if (!offlineModelManager.hasNetworkConnection()) {
            Toast.makeText(this, R.string.model_download_network_required, Toast.LENGTH_LONG).show()
            return
        }
        if (offlineModelManager.isActiveNetworkMetered()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.mobile_data_warning_title)
                .setMessage(R.string.mobile_data_warning_message)
                .setPositiveButton(R.string.continue_download) { _, _ -> performOfflineModelReinstall() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        performOfflineModelReinstall()
    }

    private fun performOfflineModelReinstall() {
        lifecycleScope.launch {
            if (deleteOfflineModelsInternal()) startOfflineModelDownload()
        }
    }

    private fun deleteOfflineModels() {
        lifecycleScope.launch { deleteOfflineModelsInternal() }
    }

    private suspend fun deleteOfflineModelsInternal(): Boolean {
        ModelDownloadService.cancel(this@MainActivity)
        stopServiceIfRunning(OverlayService::class.java)
        val result = runCatching {
            withContext(Dispatchers.IO) { offlineModelManager.removeCompletePack() }
        }
        result.onSuccess {
            Toast.makeText(this@MainActivity, R.string.offline_models_deleted, Toast.LENGTH_LONG).show()
            refreshModelStatus()
            updateButtonState()
        }.onFailure {
            Toast.makeText(this@MainActivity, "删除离线模型失败，请稍后重试。", Toast.LENGTH_LONG).show()
        }
        return result.isSuccess
    }

    private fun requestNotificationAndStartDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                MODEL_NOTIFICATION_PERMISSION_CODE,
            )
            return
        }
        startOfflineModelDownloadService()
    }

    private fun startOfflineModelDownloadService() {
        downloadModelsButton.isEnabled = false
        cancelModelDownloadButton.isEnabled = true
        modelDownloadProgress.visibility = android.view.View.VISIBLE
        ModelDownloadService.start(this)
    }

    private fun observeModelDownloadStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ModelDownloadController.status.collect { status ->
                    when (status) {
                        ModelDownloadStatus.Idle -> refreshModelStatus()
                        is ModelDownloadStatus.Running -> {
                            modelStatusText.text = status.progress.message
                            modelDownloadProgress.visibility = android.view.View.VISIBLE
                            modelDownloadProgress.progress = status.progress.percent
                            downloadModelsButton.isEnabled = false
                            cancelModelDownloadButton.visibility = android.view.View.VISIBLE
                            cancelModelDownloadButton.isEnabled = true
                        }
                        ModelDownloadStatus.Completed -> {
                            Toast.makeText(this@MainActivity, R.string.model_download_complete, Toast.LENGTH_LONG).show()
                            refreshModelStatus()
                            updateButtonState()
                            ModelDownloadController.acknowledgeTerminalStatus()
                        }
                        ModelDownloadStatus.Cancelled -> {
                            Toast.makeText(this@MainActivity, R.string.model_download_cancelled, Toast.LENGTH_SHORT).show()
                            refreshModelStatus()
                            ModelDownloadController.acknowledgeTerminalStatus()
                        }
                        is ModelDownloadStatus.Failed -> {
                            modelStatusText.text = getString(R.string.model_download_failed_detail, status.message)
                            modelDownloadProgress.visibility = android.view.View.GONE
                            downloadModelsButton.isEnabled = true
                            cancelModelDownloadButton.visibility = android.view.View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun refreshModelStatus() {
        val snapshot = offlineModelManager.snapshot()
        modelStatusText.text = getString(
            R.string.model_status_template,
            if (snapshot.asrReady) getString(R.string.status_installed) else getString(R.string.status_not_installed),
            if (snapshot.screenModelReady) getString(R.string.status_installed) else getString(R.string.status_not_installed),
            OfflineModelManager.formatBytes(snapshot.installedBytes),
        )
        modelDownloadProgress.visibility = android.view.View.GONE
        cancelModelDownloadButton.visibility = android.view.View.GONE
        downloadModelsButton.text = if (snapshot.complete) {
            getString(R.string.reinstall_offline_models)
        } else {
            getString(R.string.download_complete_offline_pack)
        }
        downloadModelsButton.isEnabled = true
        deleteModelsButton.visibility = if (snapshot.complete) android.view.View.VISIBLE else android.view.View.GONE
    }

     @Suppress("DEPRECATION")
     private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        try {
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                     Log.d("MainActivity", "Service ${serviceClass.simpleName} is running.")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking running services: ${e.message}")
        }
         Log.d("MainActivity", "Service ${serviceClass.simpleName} is not running.")
        return false
    }

     private fun stopServiceIfRunning(serviceClass: Class<*>) {
         if (isServiceRunning(serviceClass)) {
             Log.i("MainActivity", "Stopping service: ${serviceClass.simpleName}")
             stopService(Intent(this, serviceClass))
         }
     }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy called.")
    }
}
