package com.xerahs.android.core.domain.model

data class UploadResult(
    val success: Boolean,
    val url: String? = null,
    val deleteUrl: String? = null,
    val thumbnailUrl: String? = null,
    val errorMessage: String? = null,
    val destination: UploadDestination
)
