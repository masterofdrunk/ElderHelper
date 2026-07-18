package com.example.elderhelper.analyzer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NativeMiniCpmVRuntime(
    context: Context,
) : MiniCpmVRuntime {
    private val bridge = NativeMiniCpmBridge(context.applicationContext)

    override suspend fun analyze(
        modelPath: String,
        projectorPath: String,
        imageJpeg: ByteArray?,
        userQuestion: String,
    ): String {
        return analyze(
            modelPath = modelPath,
            projectorPath = projectorPath,
            imageJpeg = imageJpeg,
            userQuestion = userQuestion,
            screenText = null,
            isSensitive = false,
        )
    }

    override suspend fun analyze(
        modelPath: String,
        projectorPath: String,
        imageJpeg: ByteArray?,
        userQuestion: String,
        screenText: String?,
        isSensitive: Boolean,
    ): String {
        val image = requireNotNull(imageJpeg) { "没有获取到屏幕截图。" }
        require(image.isNotEmpty()) { "屏幕截图为空。" }
        require(userQuestion.isNotBlank()) { "问题不能为空。" }
        return bridge.analyze(modelPath, projectorPath, image, userQuestion, screenText, isSensitive)
    }

    override fun release() {
        bridge.release()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeMiniCpmBridge(
    private val context: Context,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    private var nativeInitialized = false
    private var modelLoaded = false
    private var loadedModelPath: String? = null
    private var loadedProjectorPath: String? = null
    private var hasCompletedTurn = false
    private var systemPromptApplied = false

    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun loadMmproj(mmprojPath: String, imageMaxSliceNums: Int): Int
    private external fun setImageMaxSliceNumsNative(n: Int)
    private external fun setMinicpmvVersionNative(version: Int)
    private external fun getMinicpmvVersionNative(): Int
    private external fun prepare(): Int
    private external fun systemInfo(): String
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun prefillImage(imageData: ByteArray, imageSize: Int): Int
    private external fun fullReset()
    private external fun nativeCancelGeneration()
    private external fun unload()
    private external fun shutdown()

    suspend fun analyze(
        modelPath: String,
        projectorPath: String,
        imageJpeg: ByteArray,
        userQuestion: String,
        screenText: String?,
        isSensitive: Boolean,
    ): String = withContext(dispatcher) {
        ensureNativeInitialized()
        ensureModelLoaded(modelPath, projectorPath)

        if (hasCompletedTurn) {
            fullReset()
            systemPromptApplied = false
        }
        ensureSystemPromptApplied()

        val prefillStarted = System.currentTimeMillis()
        val prefillCode = prefillImage(imageJpeg, imageJpeg.size)
        if (prefillCode != 0) {
            throw RuntimeException("MiniCPM-V 图片预处理失败（$prefillCode）。")
        }
        Log.i(TAG, "MiniCPM-V image prefill completed in ${System.currentTimeMillis() - prefillStarted} ms.")

        val prompt = ScreenGuidancePromptBuilder.buildUserPrompt(
            userQuestion = userQuestion,
            screenText = screenText,
            isSensitive = isSensitive,
        )
        val promptCode = processUserPrompt(prompt, MAX_PREDICT_TOKENS)
        if (promptCode != 0) {
            throw RuntimeException("MiniCPM-V 问题处理失败（$promptCode）。")
        }

        val generationStarted = System.currentTimeMillis()
        val response = StringBuilder()
        while (true) {
            val token = generateNextToken() ?: break
            response.append(token)
        }
        hasCompletedTurn = true
        Log.i(
            TAG,
            "MiniCPM-V generated ${response.length} chars in ${System.currentTimeMillis() - generationStarted} ms.",
        )
        response.toString().trim().ifBlank {
            throw RuntimeException("MiniCPM-V 没有生成有效回答。")
        }
    }

    fun release() {
        if (!nativeInitialized) return
        runBlocking {
            withContext(dispatcher) {
                try {
                    if (modelLoaded) unload()
                    shutdown()
                } catch (error: Throwable) {
                    Log.w(TAG, "Failed to release MiniCPM-V runtime cleanly.", error)
                } finally {
                    modelLoaded = false
                    nativeInitialized = false
                    loadedModelPath = null
                    loadedProjectorPath = null
                    hasCompletedTurn = false
                    systemPromptApplied = false
                }
            }
        }
    }

    private fun ensureNativeInitialized() {
        if (nativeInitialized) return
        try {
            System.loadLibrary(NATIVE_LIBRARY)
            init(context.applicationInfo.nativeLibraryDir)
            nativeInitialized = true
            Log.i(TAG, "MiniCPM-V native runtime initialized: ${systemInfo().lineSequence().firstOrNull().orEmpty()}")
        } catch (error: UnsatisfiedLinkError) {
            throw RuntimeException("MiniCPM-V 本地运行库加载失败。", error)
        }
    }

    private fun ensureModelLoaded(modelPath: String, projectorPath: String) {
        if (modelLoaded && loadedModelPath == modelPath && loadedProjectorPath == projectorPath) return
        if (modelLoaded) {
            unload()
            modelLoaded = false
        }

        val loadStarted = System.currentTimeMillis()
        if (load(modelPath) != 0) {
            throw RuntimeException("MiniCPM-V 语言模型加载失败。")
        }
        val projectorCode = loadMmproj(projectorPath, IMAGE_MAX_SLICE_NUMS)
        if (projectorCode != 0) {
            unload()
            throw RuntimeException("MiniCPM-V 视觉模型加载失败（$projectorCode）。")
        }
        setMinicpmvVersionNative(MINICPM_V46_VERSION)
        if (prepare() != 0) {
            unload()
            throw RuntimeException("MiniCPM-V 推理上下文创建失败。")
        }

        modelLoaded = true
        loadedModelPath = modelPath
        loadedProjectorPath = projectorPath
        hasCompletedTurn = false
        systemPromptApplied = false
        Log.i(TAG, "MiniCPM-V model loaded in ${System.currentTimeMillis() - loadStarted} ms.")
    }

    private fun ensureSystemPromptApplied() {
        if (systemPromptApplied) return
        val started = System.currentTimeMillis()
        val code = processSystemPrompt(ScreenGuidancePromptBuilder.SYSTEM_PROMPT)
        if (code != 0) {
            throw RuntimeException("MiniCPM-V 系统提示词处理失败（$code）。")
        }
        systemPromptApplied = true
        Log.i(TAG, "MiniCPM-V system prompt applied in ${System.currentTimeMillis() - started} ms.")
    }

    private companion object {
        private const val TAG = "NativeMiniCpmVRuntime"
        private const val NATIVE_LIBRARY = "elderhelper_minicpm"
        private const val MINICPM_V46_VERSION = 46
        private const val IMAGE_MAX_SLICE_NUMS = 1
        private const val MAX_PREDICT_TOKENS = 160
    }
}
