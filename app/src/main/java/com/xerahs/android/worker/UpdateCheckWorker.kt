package com.xerahs.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xerahs.android.feature.settings.data.GitHubReleaseChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val releaseChecker: GitHubReleaseChecker
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val currentVersion = try {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName ?: return Result.failure()
        } catch (_: Exception) {
            return Result.failure()
        }

        val result = releaseChecker.fetchLatestRelease()

        return result.fold(
            onSuccess = { release ->
                val updateAvailable = releaseChecker.isNewerVersion(release.tagName, currentVersion)

                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_UPDATE_AVAILABLE, updateAvailable)
                    .putString(KEY_LATEST_VERSION, release.tagName)
                    .apply()

                Result.success()
            },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        const val WORK_NAME = "update_check"
        const val PREFS_NAME = "update_prefs"
        const val KEY_UPDATE_AVAILABLE = "update_available"
        const val KEY_LATEST_VERSION = "latest_version"
    }
}
