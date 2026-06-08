package com.example.elderhelper.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AndroidTtsSpeaker(
    context: Context,
) : SpeechSpeaker, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    @Volatile
    override var isReady: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed: $status")
            isReady = false
            return
        }

        val languageResult = tts?.setLanguage(Locale.CHINESE)
        isReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun speak(text: String) {
        val cleaned = text.replace("*", "").trim()
        if (cleaned.isBlank() || !isReady) return

        val utteranceId = "elderhelper-${System.currentTimeMillis()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (ignored: Exception) {
        }
        tts = null
        isReady = false
    }

    private companion object {
        private const val TAG = "AndroidTtsSpeaker"
    }
}
