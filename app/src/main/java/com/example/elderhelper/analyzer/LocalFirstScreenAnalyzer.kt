package com.example.elderhelper.analyzer

import android.content.Context
import android.graphics.Bitmap
import com.example.elderhelper.model.ModelAssetManager
import com.example.elderhelper.privacy.SensitiveOperationGuard

internal class LocalFirstScreenAnalyzer(
    context: Context,
    private val sensitiveOperationGuard: SensitiveOperationGuard = SensitiveOperationGuard(),
    private val screenGuidanceAgent: ScreenGuidanceAgent = ScreenGuidanceAgent(
        AssetLocalKnowledgeRetriever(context.applicationContext),
    ),
    private val bitmapPreprocessor: ScreenBitmapPreprocessor = ScreenBitmapPreprocessor(),
    private val runtime: MiniCpmVRuntime = NativeMiniCpmVRuntime(context.applicationContext),
) : ScreenAnalyzer {
    private val modelAssetManager = ModelAssetManager(context)

    override suspend fun analyze(
        screenBitmap: Bitmap?,
        screenText: String?,
        userQuestion: String,
    ): AnalyzerResult {
        val sensitivePrefix = sensitiveOperationGuard.warningFor(userQuestion, screenText)
        when (val plan = screenGuidanceAgent.plan(userQuestion, screenText, sensitivePrefix)) {
            is ScreenGuidancePlan.LocalAnswer -> {
                return AnalyzerResult(
                    guidance = plan.guidance,
                    isSensitive = sensitivePrefix.isNotBlank(),
                )
            }
            ScreenGuidancePlan.NeedsVisionModel -> Unit
        }

        // Never wake the visual model when the capture itself failed. There is no image
        // evidence for it to inspect, so a short recovery instruction is more useful and
        // avoids making the person wait for a request that cannot succeed.
        if (screenBitmap == null) {
            return AnalyzerResult(
                guidance = "我没有获取到当前屏幕。请回到要操作的页面，重新打开“看屏”权限后再试；也可以直接说出屏幕上能看到的文字。",
                isSensitive = sensitivePrefix.isNotBlank(),
                errorMessage = "屏幕截图未获取到，未启动离线看屏模型。",
            )
        }

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
                screenText = screenText,
                isSensitive = sensitivePrefix.isNotBlank(),
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
                append("我这次没有看清页面。请回到要操作的页面，重新打开看屏后再试；如果还是不行，可以直接说出屏幕上的文字。")
            }
            AnalyzerResult(
                guidance = guidance,
                isSensitive = sensitivePrefix.isNotBlank(),
                errorMessage = "离线看屏分析失败；已提示用户重新获取屏幕，而不是重复启动模型。",
            )
        }
    }

    override fun release() {
        runtime.release()
    }
}
