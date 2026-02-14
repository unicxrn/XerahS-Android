package com.xerahs.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class XerahSApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for screen capture service"
        }

        val uploadChannel = NotificationChannel(
            CHANNEL_UPLOAD,
            "Upload Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for file upload progress"
        }

        val quickCaptureChannel = NotificationChannel(
            CHANNEL_QUICK_CAPTURE,
            "Quick Capture",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Quick capture notification action"
        }

        manager.createNotificationChannel(captureChannel)
        manager.createNotificationChannel(uploadChannel)
        manager.createNotificationChannel(quickCaptureChannel)
    }

    companion object {
        const val CHANNEL_CAPTURE = "capture_channel"
        const val CHANNEL_UPLOAD = "upload_channel"
        const val CHANNEL_QUICK_CAPTURE = "quick_capture_channel"
    }
}
