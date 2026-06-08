package com.example.elderhelper.analyzer

import android.content.Context
import android.graphics.Bitmap
import com.example.elderhelper.model.ModelAssetManager
import com.example.elderhelper.privacy.SensitiveOperationGuard

class LocalFirstScreenAnalyzer(
    context: Context,
    private val sensitiveOperationGuard: SensitiveOperationGuard = SensitiveOperationGuard(),
    private val bitmapPreprocessor: ScreenBitmapPreprocessor = ScreenBitmapPreprocessor(),
    private val runtime: MiniCpmVRuntime = UnavailableMiniCpmVRuntime(),
) : ScreenAnalyzer {
    private val modelAssetManager = ModelAssetManager(context)

    override suspend fun analyze(
        screenBitmap: Bitmap?,
        screenText: String?,
        userQuestion: String,
    ): AnalyzerResult {
        val sensitivePrefix = sensitiveOperationGuard.warningFor(userQuestion, screenText)

        if (!modelAssetManager.isMiniCpmModelInstalled()) {
            val files = modelAssetManager.miniCpmModelFiles()
            val fallback = buildString {
                append(sensitivePrefix)
                append("还没有安装离线看屏模型。请先安装模型包，然后再试一次。")
            }
            return AnalyzerResult(
                guidance = fallback,
                isSensitive = sensitivePrefix.isNotBlank(),
                errorMessage = "离线看屏模型未安装：${files.model.name} / ${files.projector.name}",
            )
        }

        val files = modelAssetManager.miniCpmModelFiles()
        val imageJpeg = bitmapPreprocessor.toJpeg(screenBitmap)
        return try {
            val guidance = runtime.analyze(
                modelPath = files.model.absolutePath,
                projectorPath = files.projector.absolutePath,
                imageJpeg = imageJpeg,
                userQuestion = userQuestion,
            )
            AnalyzerResult(
                guidance = sensitivePrefix + guidance.trim(),
                isSensitive = sensitivePrefix.isNotBlank(),
            )
        } catch (e: IllegalStateException) {
            val guidance = buildString {
                append(sensitivePrefix)
                append("离线看屏运行库还没有接入。语音已经识别成功，下一步需要接入 MiniCPM-V 的 llama.cpp Android 运行库。")
            }
            AnalyzerResult(
                guidance = guidance,
                isSensitive = sensitivePrefix.isNotBlank(),
                errorMessage = "离线看屏运行库未接入。",
            )
        } catch (e: RuntimeException) {
            val guidance = buildString {
                append(sensitivePrefix)
                append("离线看屏分析失败，请检查模型文件是否完整。")
            }
            AnalyzerResult(
                guidance = guidance,
                isSensitive = sensitivePrefix.isNotBlank(),
                errorMessage = "离线看屏分析失败。",
            )
        }
    }

    override fun release() {
        runtime.release()
    }
}
