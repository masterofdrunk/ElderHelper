package com.example.elderhelper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class ScreenCaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("ScreenCaptureService", "onCreate called")
        // Initialization for screen capture will go here (e.g., startForeground)
    }

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenCaptureService", "onStartCommand called")
        // Logic to handle MediaProjection intent data and start capture will go here
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ScreenCaptureService", "onDestroy called")
        // Cleanup logic for screen capture will go here
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 