package com.xerahs.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xerahs.android.core.common.file.CaptureSweeper
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.xerahs.android.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp
class XerahSApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var historyRepository: HistoryRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleUpdateCheck()
        configureCoil()
        sweepOldCopies()
    }

    /**
     * Deletes week-old pick/share copies. WorkManager does not expose a work's input data, so
     * while any upload is queued, running or blocked the sweep is skipped; files that history
     * points to are always kept.
     */
    private fun sweepOldCopies() {
        appScope.launch {
            runCatching {
                val active = WorkManager.getInstance(this@XerahSApplication)
                    .getWorkInfosByTagFlow(UPLOAD_WORK_TAG).first()
                    .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
                if (active) return@launch
                val referenced = historyRepository.getAllHistory().first().map { it.filePath }.toSet()
                CaptureSweeper.sweep(File(filesDir, "captures"), System.currentTimeMillis(), referenced)
            }
        }
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
