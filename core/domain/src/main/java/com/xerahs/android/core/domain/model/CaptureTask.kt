package com.xerahs.android.core.domain.model

data class CaptureTask(
    val id: String,
    val filePath: String,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val status: CaptureStatus = CaptureStatus.CAPTURED
)

enum class CaptureStatus {
    CAPTURED,
    ANNOTATING,
    UPLOADING,
    UPLOADED,
    FAILED
}
