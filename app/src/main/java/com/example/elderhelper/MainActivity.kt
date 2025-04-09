package com.example.elderhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private val OVERLAY_PERMISSION_REQUEST_CODE = 102
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var hasRecordAudioPermission = false
    private var hasOverlayPermission = false
    private var mediaProjectionIntent: Intent? = null
    private var mediaProjectionResultCode: Int = 0

    private lateinit var checkPermissionsButton: Button

    companion object {
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.elderhelper.ACTION_MEDIA_PROJECTION_RESULT"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
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
        } catch (e: Exception) {
             Log.e("MainActivity", "Error finding button R.id.checkPermissionsButton. Check layout file and build.", e)
             Toast.makeText(this, "无法找到界面按钮", Toast.LENGTH_LONG).show()
             finish()
             return
        }

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
        updateButtonState()

    }

    private fun checkPermissionStatus(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

     private fun checkOverlayPermissionStatus(): Boolean {
         return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
             Settings.canDrawOverlays(this)
         } else {
             true
         }
    }

    private fun checkAndRequestAllPermissions() {
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
          val serviceRunning = isServiceRunning(OverlayService::class.java)
          val allPermissionsGranted = hasAllPermissions()

          if (serviceRunning) {
              checkPermissionsButton.text = "助手运行中"
              checkPermissionsButton.isEnabled = false
          } else if (allPermissionsGranted) {
               checkPermissionsButton.text = "启动助手服务"
               checkPermissionsButton.isEnabled = true
          } else {
               var missingPerms = mutableListOf<String>()
               if (!checkPermissionStatus(Manifest.permission.RECORD_AUDIO)) missingPerms.add("录音")
               if (!checkOverlayPermissionStatus()) missingPerms.add("悬浮窗")
               if (mediaProjectionResultCode != RESULT_OK || mediaProjectionIntent == null) missingPerms.add("屏幕捕获")

              if (missingPerms.isEmpty()) {
                   checkPermissionsButton.text = "检查权限"
               } else {
                   checkPermissionsButton.text = "请求权限 (${missingPerms.joinToString()})"
               }
               checkPermissionsButton.isEnabled = true
          }

         var statusText = "权限: "
         statusText += if (hasRecordAudioPermission) "录✓ " else "录✗ "
         statusText += if (hasOverlayPermission) "悬✓ " else "悬✗ "
         statusText += if (mediaProjectionResultCode == RESULT_OK && mediaProjectionIntent != null) "屏✓" else "屏✗"
         statusText += " | 服务: ${if(serviceRunning) "运行中" else "未运行"}"
         title = statusText
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

