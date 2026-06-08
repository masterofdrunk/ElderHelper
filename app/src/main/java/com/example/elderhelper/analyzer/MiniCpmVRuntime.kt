package com.example.elderhelper.analyzer

import java.io.File

interface MiniCpmVRuntime {
    suspend fun analyze(
        modelPath: String,
        projectorPath: String,
        imageJpeg: ByteArray?,
        userQuestion: String,
    ): String

    fun release()
}

class UnavailableMiniCpmVRuntime : MiniCpmVRuntime {
    override suspend fun analyze(
        modelPath: String,
        projectorPath: String,
        imageJpeg: ByteArray?,
        userQuestion: String,
    ): String {
        throw IllegalStateException("MiniCPM-V runtime is not linked yet.")
    }

    override fun release() = Unit
}

data class MiniCpmVRuntimeInput(
    val modelFile: File,
    val projectorFile: File,
    val imageJpeg: ByteArray?,
    val userQuestion: String,
)
