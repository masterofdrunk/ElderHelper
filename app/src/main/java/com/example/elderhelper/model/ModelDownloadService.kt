package com.example.elderhelper.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.elderhelper.MainActivity
import com.example.elderhelper.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ModelDownloadStatus {
    data object Idle : ModelDownloadStatus()
    data class Running(val progress: OfflineModelProgress) : ModelDownloadStatus()
    data object Completed : ModelDownloadStatus()
    data object Cancelled : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
}

object ModelDownloadController {
    private val mutableStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
    val status: StateFlow<ModelDownloadStatus> = mutableStatus.asStateFlow()

    internal fun update(status: ModelDownloadStatus) {
        mutableStatus.value = status
    }

    fun acknowledgeTerminalStatus() {
        if (mutableStatus.value !is ModelDownloadStatus.Running) {
            mutableStatus.value = ModelDownloadStatus.Idle
        }
    }
}

class ModelDownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val downloader = ResumableModelDownloader()
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            downloader.cancelActiveDownload()
            downloadJob?.cancel()
            ModelDownloadController.update(ModelDownloadStatus.Cancelled)
            stopSelf()
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) return START_NOT_STICKY

        val initial = OfflineModelProgress("正在准备离线模型下载", 0L, 1L)
        startDownloadForeground(buildNotification(initial.message, 0))
        acquireWakeLock()
        ModelDownloadController.update(ModelDownloadStatus.Running(initial))
        downloadJob = serviceScope.launch {
            try {
                OfflineModelManager(applicationContext, downloader).installCompletePack { progress ->
                    ModelDownloadController.update(ModelDownloadStatus.Running(progress))
                    notifyProgress(progress)
                }
                ModelDownloadController.update(ModelDownloadStatus.Completed)
                showTerminalNotification(getString(R.string.model_download_complete))
            } catch (cancelled: CancellationException) {
                ModelDownloadController.update(ModelDownloadStatus.Cancelled)
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: getString(R.string.model_download_failed)
                ModelDownloadController.update(ModelDownloadStatus.Failed(message))
                showTerminalNotification(message)
            } finally {
                releaseWakeLock()
                stopDownloadForeground()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownloadForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(progress: OfflineModelProgress) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(progress.message, progress.percent))
    }

    private fun showTerminalNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(R.string.model_download_notification_title))
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun buildNotification(message: String, percent: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.model_download_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), cancel)
            .build()
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElderHelper:ModelDownload").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopDownloadForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    companion object {
        private const val CHANNEL_ID = "offline_model_download"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TIMEOUT_MS = 2L * 60L * 60L * 1000L
        private const val ACTION_START = "com.example.elderhelper.action.MODEL_DOWNLOAD_START"
        private const val ACTION_CANCEL = "com.example.elderhelper.action.MODEL_DOWNLOAD_CANCEL"

        fun start(context: Context) {
            ensureNotificationChannel(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, ModelDownloadService::class.java).setAction(ACTION_START),
            )
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.model_download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }
}
