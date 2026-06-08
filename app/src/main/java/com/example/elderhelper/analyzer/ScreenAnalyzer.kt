package com.example.elderhelper.analyzer

import android.graphics.Bitmap

interface ScreenAnalyzer {
    suspend fun analyze(
        screenBitmap: Bitmap?,
        screenText: String?,
        userQuestion: String,
    ): AnalyzerResult

    fun release()
}
