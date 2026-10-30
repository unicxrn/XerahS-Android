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
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Wakes when MediaStore images change and handles screenshots added since the last check.
 * A content-URI trigger fires once per work request, so two slots (`-a` and `-b`) take turns:
 * each run arms the other slot first, so a screenshot taken mid-run still has a watcher.
 */
@HiltWorker
class ScreenshotWatchWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.getScreenshotWatchEnabled().first()) return Result.success()
        if (appContext.screenshotAccess() == ScreenshotAccess.DENIED) {
            // Permission was revoked: stop watching and remember why, so settings can explain.
            settingsRepository.setScreenshotWatchEnabled(false)
            settingsRepository.setScreenshotPermissionLost(true)
            cancel(appContext)
            return Result.success()
        }
        val thisSlot = params.inputData.getString(KEY_SLOT) ?: SLOT_A
        enqueue(appContext, if (thisSlot == SLOT_A) SLOT_B else SLOT_A, replace = false)
        // Both slots can be triggered at once; one pass at a time, the seen ids keep it exact.
        passLock.withLock { pass() }
        return Result.success()
    }

    private suspend fun pass() {
        val auto = settingsRepository.getScreenshotAutoUpload().first()
        val lastCheck = settingsRepository.getLastScreenshotCheck()
        val seen = ScreenshotScan.parseSeen(settingsRepository.getScreenshotSeenIds())
        val seenSet = seen.toHashSet()
        val rows = runCatching { query(ScreenshotScan.queryFrom(lastCheck)) }.getOrDefault(emptyList())

        val handledIds = mutableListOf<Long>()
        val handledDates = mutableListOf<Long>()
        for (shot in rows) {
            if (!ScreenshotScan.isNew(shot.id, seenSet)) { handledDates += shot.dateAdded; continue }
            if (!shot.isScreenshot) { handledDates += shot.dateAdded; continue }
            val ok = runCatching { handle(shot, auto) }.getOrDefault(false)
            if (ok) {
                handledIds += shot.id
                handledDates += shot.dateAdded
            }
        }
        if (handledIds.isNotEmpty()) {
            settingsRepository.setScreenshotSeenIds(ScreenshotScan.encodeSeen(ScreenshotScan.mergeSeen(seen, handledIds)))
        }
        settingsRepository.setLastScreenshotCheck(ScreenshotScan.nextCheck(lastCheck, handledDates))
    }

    /** Uploads straight away when auto is on; if that fails, or auto is off, offers the notification. */
    private suspend fun handle(shot: Shot, auto: Boolean): Boolean {
        if (auto) {
            val uploaded = runCatching {
                ScreenshotUploads.copyAndEnqueue(appContext, shot.uri, settingsRepository, profileRepository)
            }.isSuccess
            if (uploaded) return true
        }
        return ScreenshotNotifier.show(appContext, shot.id, shot.uri)
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
            collection, projection, "${MediaStore.Images.Media.DATE_ADDED} >= ?", arrayOf(since.toString()),
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
        const val WORK_TAG = "screenshot-watch"
        const val SLOT_A = "a"
        const val SLOT_B = "b"
        private const val KEY_SLOT = "slot"
        private val passLock = Mutex()

        private fun nameFor(slot: String) = "$WORK_TAG-$slot"

        /** Arms one slot. KEEP never cancels a run that is already in progress. */
        fun enqueue(context: Context, slot: String = SLOT_A, replace: Boolean = false) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(1, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(5, TimeUnit.SECONDS)
                .build()
            val request = OneTimeWorkRequestBuilder<ScreenshotWatchWorker>()
                .setConstraints(constraints)
                .setInputData(androidx.work.Data.Builder().putString(KEY_SLOT, slot).build())
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                nameFor(slot),
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
