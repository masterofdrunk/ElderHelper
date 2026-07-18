package com.example.elderhelper.analyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.elderhelper.model.ModelAssetManager
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniCpmVRuntimeInstrumentedTest {
    @Test
    fun analyzesFixedChineseSettingsScreenshot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = ModelAssetManager(context).miniCpmModelFiles()
        assumeTrue("MiniCPM-V model is not installed.", files.model.isFile)
        assumeTrue("MiniCPM-V projector is not installed.", files.projector.isFile)

        val imageJpeg = createSettingsScreenshotJpeg()
        val runtime = NativeMiniCpmVRuntime(context)
        try {
            val result = runtime.analyze(
                modelPath = files.model.absolutePath,
                projectorPath = files.projector.absolutePath,
                imageJpeg = imageJpeg,
                userQuestion = "我想连接无线网络，应该点哪里？",
            )
            Log.i(TAG, "Synthetic screenshot result: $result")
            assertTrue("Expected a non-empty local screen guidance result.", result.isNotBlank())
            assertTrue(
                "Expected the guidance to reference the visible network entry.",
                result.contains("无线") || result.contains("网络") || result.contains("Wi-Fi", ignoreCase = true),
            )
        } finally {
            runtime.release()
        }
    }

    private fun createSettingsScreenshotJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 32, 32)
            textSize = 24f
        }
        canvas.drawText("设置", 28f, 60f, titlePaint)
        canvas.drawText("无线网络", 36f, 140f, itemPaint)
        canvas.drawText("蓝牙", 36f, 210f, itemPaint)
        canvas.drawText("显示与亮度", 36f, 280f, itemPaint)
        canvas.drawText("声音和振动", 36f, 350f, itemPaint)
        canvas.drawText("隐私", 36f, 420f, itemPaint)

        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "MiniCpmVRuntimeTest"
    }
}
