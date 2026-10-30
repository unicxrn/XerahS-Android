package com.xerahs.android.feature.upload.screenshots

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xerahs.android.core.common.permission.ScreenshotAccess
import com.xerahs.android.core.common.permission.screenshotAccess
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Wakes when MediaStore images change, looks for screenshots added since the last check, then
 * schedules itself again. A content-URI trigger fires once per work request, hence the re-enqueue.
 */
@HiltWorker
class ScreenshotWatchWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.getScreenshotWatchEnabled().first()) return Result.success()
        if (appContext.screenshotAccess() == ScreenshotAccess.DENIED) {
            // Permission was revoked: stop watching and turn the toggle off instead of looping.
            settingsRepository.setScreenshotWatchEnabled(false)
            return Result.success()
        }
        val auto = settingsRepository.getScreenshotAutoUpload().first()
        val destination = if (auto) settingsRepository.getDefaultDestination().first() else null
        val lastCheck = settingsRepository.getLastScreenshotCheck()

        var newest = lastCheck
        runCatching {
            for (shot in query(lastCheck)) {
                newest = maxOf(newest, shot.dateAdded)
                if (!shot.isScreenshot) continue
                if (destination != null) {
                    runCatching { ScreenshotUploads.copyAndEnqueue(appContext, shot.uri, destination) }
                } else {
                    ScreenshotNotifier.show(appContext, shot.id, shot.uri)
                }
            }
        }
        settingsRepository.setLastScreenshotCheck(newest)
        enqueue(appContext, replace = true)
        return Result.success()
    }

    private class Shot(val id: Long, val uri: Uri, val dateAdded: Long, val isScreenshot: Boolean)

    private fun query(since: Long): List<Shot> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val hasPath = Build.VERSION.SDK_INT >= 29
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (hasPath) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()
        val out = mutableListOf<Shot>()
        appContext.contentResolver.query(
            collection, projection, "${MediaStore.Images.Media.DATE_ADDED} > ?", arrayOf(since.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathCol = if (hasPath) c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH) else -1
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += Shot(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    dateAdded = c.getLong(addedCol),
                    isScreenshot = isScreenshot(
                        if (pathCol >= 0) c.getString(pathCol) else null,
                        c.getString(bucketCol),
                    ),
                )
            }
        }
        return out
    }

    companion object {
        const val WORK_NAME = "screenshot-watch"

        /** [replace] is true from inside the worker; app start uses KEEP so a pending trigger isn't reset. */
        fun enqueue(context: Context, replace: Boolean) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(1, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(5, TimeUnit.SECONDS)
                .build()
            val request = OneTimeWorkRequestBuilder<ScreenshotWatchWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
