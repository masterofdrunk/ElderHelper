package com.example.elderhelper.speech

interface SpeechToTextEngine {
    val isRecording: Boolean

    fun startListening()

    suspend fun stopAndTranscribe(): SttResult

    fun cancel()

    fun release()
}

data class SttResult(
    val text: String? = null,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean
        get() = !text.isNullOrBlank()
}
