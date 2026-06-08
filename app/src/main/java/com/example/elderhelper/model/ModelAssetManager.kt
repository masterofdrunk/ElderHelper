package com.example.elderhelper.model

import android.content.Context
import java.io.File

class ModelAssetManager(
    private val context: Context,
) {
    fun miniCpmModelDir(): File = File(context.filesDir, "models/minicpm-v")

    fun miniCpmModelFiles(): MiniCpmModelFiles {
        val dir = miniCpmModelDir()
        return MiniCpmModelFiles(
            dir = dir,
            model = File(dir, MINICPM_MODEL_FILE),
            projector = File(dir, MINICPM_PROJECTOR_FILE),
        )
    }

    fun sherpaAsrModelDir(): File = File(context.filesDir, "sherpa-onnx/asr")

    fun isMiniCpmModelInstalled(): Boolean {
        val files = miniCpmModelFiles()
        return files.model.exists() && files.projector.exists()
    }

    companion object {
        const val MINICPM_MODEL_FILE = "MiniCPM-V-4_6-Q4_K_M.gguf"
        const val MINICPM_PROJECTOR_FILE = "mmproj-model-f16.gguf"
    }
}

data class MiniCpmModelFiles(
    val dir: File,
    val model: File,
    val projector: File,
)
