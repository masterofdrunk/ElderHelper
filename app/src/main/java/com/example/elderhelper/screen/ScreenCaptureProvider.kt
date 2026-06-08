package com.example.elderhelper.screen

import android.graphics.Bitmap

interface ScreenCaptureProvider {
    suspend fun capture(): Bitmap?

    fun release()
}
