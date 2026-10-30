package com.xerahs.android.core.domain.model

data class UploadResult(
    val success: Boolean,
    val url: String? = null,
    val deleteUrl: String? = null,
    val remoteKey: String? = null,
    val thumbnailUrl: String? = null,
    val errorMessage: String? = null,
    val destination: UploadDestination,
    /** HTTP status of a failed upload, when the server answered. Used to decide whether to retry. */
    val httpCode: Int? = null,
    /** What made the upload fail, when it threw. Used to decide whether to retry. */
    val error: Throwable? = null,
    /** The uploader's own verdict: true means retrying can't help, false means it can. Null: decide from the code and error. */
    val permanent: Boolean? = null,
)
