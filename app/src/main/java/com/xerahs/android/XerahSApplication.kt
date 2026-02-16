package com.xerahs.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xerahs.android.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class XerahSApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleUpdateCheck()
    }

    private fun scheduleUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
