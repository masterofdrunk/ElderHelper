package com.example.elderhelper.analyzer

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class ScreenBitmapPreprocessor(
    private val maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
    private val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    fun toJpeg(bitmap: Bitmap?): ByteArray? {
        bitmap ?: return null
        val scaled = scaleToFit(bitmap)
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
                output.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    private fun scaleToFit(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        // MiniCPM-V's vision pass dominates latency on mid-range mobile CPUs. A 640 px
        // long edge kept the Chinese settings text readable while cutting the tested
        // prefill time on HONOR NTN-AN20 from about 105 s to about 48 s.
        private const val DEFAULT_MAX_LONG_EDGE = 640
        private const val DEFAULT_JPEG_QUALITY = 85
    }
}
