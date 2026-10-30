package com.xerahs.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.xerahs.android.core.common.file.CaptureSweeper
import com.xerahs.android.core.common.permission.ScreenshotAccess
import com.xerahs.android.core.common.permission.screenshotAccess
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.upload.screenshots.ScreenshotNotifier
import com.xerahs.android.feature.upload.screenshots.ScreenshotWatchWorker
import com.xerahs.android.feature.upload.worker.RetryFiles
import com.xerahs.android.feature.upload.worker.StoppedUploadStore
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.shortcuts.ProfileShortcutPublisher
import com.xerahs.android.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var profileShortcuts: ProfileShortcutPublisher

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleUpdateCheck()
        configureCoil()
        sweepOldCopies()
        watchScreenshotSetting()
        profileShortcuts.start(appScope)
    }

    /** Keeps the screenshot watcher scheduled while the setting is on, and cancels it when it is off. */
    private fun watchScreenshotSetting() {
        appScope.launch {
            settingsRepository.getScreenshotWatchEnabled().distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    ScreenshotWatchWorker.cancel(this@XerahSApplication)
                } else if (screenshotAccess() == ScreenshotAccess.DENIED) {
                    // Permission was revoked in system settings: turn the toggle off.
                    settingsRepository.setScreenshotWatchEnabled(false)
                    settingsRepository.setScreenshotPermissionLost(true)
                } else {
                    ScreenshotWatchWorker.enqueue(this@XerahSApplication)
                }
            }
        }
    }

    /**
     * Deletes week-old pick/share copies and editor exports, and day-old tool output. WorkManager does not expose a work's input data, so
     * while any upload is queued, running or blocked the sweep is skipped. Files that history
     * points to are always kept, and so are files a failed or stopped upload from the last week could retry.
     */
    private fun sweepOldCopies() {
        appScope.launch {
            runCatching {
                val infos = WorkManager.getInstance(this@XerahSApplication)
                    .getWorkInfosByTagFlow(UPLOAD_WORK_TAG).first()
                val active = infos
                    .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
                if (active) return@launch
                val now = System.currentTimeMillis()
                val retryable = RetryFiles.referencedPaths(infos, StoppedUploadStore.forCache(cacheDir).all().values, now)
                val referenced = historyRepository.getAllHistory().first().map { it.filePath }.toSet() + retryable
                CaptureSweeper.sweep(File(filesDir, "captures"), now, referenced)
                // Tool renders (tools_out), copy folders a closed tool left behind (tools_combine,
                // tools_beautify), and week-old editor exports (filesDir/exports).
                CaptureSweeper.sweepToolCache(cacheDir, now, referenced, exportsDir = File(filesDir, "exports"))
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

        val screenshotChannel = NotificationChannel(
            ScreenshotNotifier.CHANNEL,
            "Screenshots",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Offers to upload new screenshots"
        }

        manager.createNotificationChannel(screenshotChannel)
        manager.createNotificationChannel(uploadChannel)
        manager.createNotificationChannel(uploadCompleteChannel)
    }

    companion object {
        const val CHANNEL_UPLOAD = "upload_channel"
        const val CHANNEL_UPLOAD_COMPLETE = "upload_complete_channel"
    }
}
