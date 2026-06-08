package com.example.elderhelper.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MediaProjectionScreenCaptureProvider(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent,
) : ScreenCaptureProvider {
    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val captureThread = HandlerThread("ElderHelperScreenCapture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    override suspend fun capture(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val metrics = displayMetrics()
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        try {
            releaseCaptureOnly()
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            mediaProjection = projection

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                projection.registerCallback(object : MediaProjection.Callback() {}, captureHandler)
            }

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ availableReader ->
                val bitmap = acquireBitmap(availableReader, width, height)
                releaseCaptureOnly()
                if (continuation.isActive) {
                    continuation.resume(bitmap)
                } else {
                    bitmap?.recycle()
                }
            }, captureHandler)

            virtualDisplay = projection.createVirtualDisplay(
                "ElderHelperCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler,
            )
        } catch (e: Exception) {
            releaseCaptureOnly()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }

        continuation.invokeOnCancellation {
            releaseCaptureOnly()
        }
    }

    override fun release() {
        releaseCaptureOnly()
        captureThread.quitSafely()
    }

    private fun acquireBitmap(reader: ImageReader, width: Int, height: Int): Bitmap? {
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: return null
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            paddedBitmap.copyPixelsFromBuffer(buffer)
            if (paddedWidth == width) {
                paddedBitmap
            } else {
                Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                    paddedBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                image?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun displayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun releaseCaptureOnly() {
        try {
            virtualDisplay?.release()
        } catch (ignored: Exception) {
        }
        try {
            imageReader?.close()
        } catch (ignored: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (ignored: Exception) {
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
