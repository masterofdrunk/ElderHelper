package com.example.elderhelper.tts

interface SpeechSpeaker {
    val isReady: Boolean

    fun speak(text: String)

    fun release()
}
