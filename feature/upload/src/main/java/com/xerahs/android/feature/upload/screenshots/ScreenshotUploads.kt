package com.xerahs.android.feature.upload.screenshots

import android.content.Context
import android.net.Uri
import com.xerahs.android.core.common.file.copyToCache
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

internal object ScreenshotUploads {
    /**
     * Copies the screenshot into app storage (a `captures/shared_*` folder, so the sweep rules
     * apply) and queues an upload with the default destination and its default profile.
     * Duplicate detection stays on. The MediaStore URI is not needed afterwards.
     */
    suspend fun copyAndEnqueue(
        context: Context,
        uri: Uri,
        settings: SettingsRepository,
        profiles: UploadProfileRepository,
    ) {
        val destination = settings.getDefaultDestination().first()
        val profileId = profiles.getDefaultProfile(destination)?.id
        val dir = File(context.filesDir, "captures/shared_${UUID.randomUUID()}")
        val file = try {
            copyToCache(context.contentResolver, uri, dir)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        UploadEnqueuer.enqueue(
            context,
            UploadSpec(paths = listOf(file.absolutePath), destination = destination, profileId = profileId),
        )
    }
}
