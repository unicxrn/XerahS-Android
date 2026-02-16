package com.xerahs.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.xerahs.android.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class XerahSApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleUpdateCheck()
        configureCoil()
    }

    private fun configureCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("s3_thumbnails"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
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

        val uploadChannel = NotificationChannel(
            CHANNEL_UPLOAD,
            "Upload Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for file upload progress"
        }

        val uploadCompleteChannel = NotificationChannel(
            CHANNEL_UPLOAD_COMPLETE,
            "Upload Complete",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for completed uploads"
        }

        manager.createNotificationChannel(uploadChannel)
        manager.createNotificationChannel(uploadCompleteChannel)
    }

    companion object {
        const val CHANNEL_UPLOAD = "upload_channel"
        const val CHANNEL_UPLOAD_COMPLETE = "upload_complete_channel"
    }
}
