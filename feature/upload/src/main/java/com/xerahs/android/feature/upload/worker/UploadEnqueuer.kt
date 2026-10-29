package com.xerahs.android.feature.upload.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.xerahs.android.core.domain.model.UploadDestination
import java.util.UUID
import java.util.concurrent.TimeUnit

const val UPLOAD_WORK_TAG = "upload"

/** Everything needed to (re)start an upload. Round-trips through WorkManager [Data]. */
data class UploadSpec(
    val paths: List<String>,
    val destination: UploadDestination,
    val profileId: String? = null,
    val albumId: String? = null,
    val tagIds: List<String> = emptyList(),
    val inputValues: Map<String, String> = emptyMap(),
    val skipDuplicateCheck: Boolean = false,
) {
    companion object {
        fun from(data: Data): UploadSpec? = fromStrings(data.keyValueMap.mapValues { it.value?.toString() })

        /** Rebuilds a spec from string values, e.g. the extras of a Retry notification action. */
        fun fromStrings(values: Map<String, String?>): UploadSpec? {
            val destination = values[UploadWorker.KEY_DESTINATION]
                ?.let { name -> UploadDestination.entries.firstOrNull { it.name == name } }
                ?: return null
            val paths = (
                values[UploadWorker.KEY_FILE_PATHS]?.split("|")?.filter { it.isNotBlank() }
                    ?: listOfNotNull(values[UploadWorker.KEY_FILE_PATH]?.takeIf { it.isNotBlank() })
                ).distinct()
            if (paths.isEmpty()) return null
            return UploadSpec(
                paths = paths,
                destination = destination,
                profileId = values[UploadWorker.KEY_PROFILE_ID],
                albumId = values[UploadWorker.KEY_ALBUM_ID],
                tagIds = values[UploadWorker.KEY_TAG_IDS]?.split("|")?.filter { it.isNotBlank() }.orEmpty(),
                inputValues = UploadWorker.decodeInputValues(values[UploadWorker.KEY_INPUT_VALUES]),
                skipDuplicateCheck = values[UploadWorker.KEY_SKIP_DUPLICATE_CHECK].toBoolean(),
            )
        }
    }
}

object UploadEnqueuer {

    fun inputData(spec: UploadSpec): Data {
        val builder = Data.Builder()
            .putString(UploadWorker.KEY_DESTINATION, spec.destination.name)
            .putBoolean(UploadWorker.KEY_SKIP_DUPLICATE_CHECK, spec.skipDuplicateCheck)
        spec.profileId?.let { builder.putString(UploadWorker.KEY_PROFILE_ID, it) }
        spec.albumId?.let { builder.putString(UploadWorker.KEY_ALBUM_ID, it) }
        if (spec.tagIds.isNotEmpty()) builder.putString(UploadWorker.KEY_TAG_IDS, spec.tagIds.joinToString("|"))
        if (spec.inputValues.isNotEmpty()) {
            builder.putString(UploadWorker.KEY_INPUT_VALUES, UploadWorker.encodeInputValues(spec.inputValues))
        }
        val paths = spec.paths.distinct()
        if (paths.size == 1) builder.putString(UploadWorker.KEY_FILE_PATH, paths.first())
        else builder.putString(UploadWorker.KEY_FILE_PATHS, paths.joinToString("|"))
        return builder.build()
    }

    /** Enqueues a tagged, uniquely named upload so it can be listed and cancelled. Returns the work id. */
    fun enqueue(context: Context, spec: UploadSpec): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (spec.destination == UploadDestination.LOCAL) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED
            )
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData(spec))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(UPLOAD_WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("upload-${request.id}", ExistingWorkPolicy.KEEP, request)
        return request.id
    }
}
