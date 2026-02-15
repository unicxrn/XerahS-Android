package com.xerahs.android.core.domain.model

data class HistoryItem(
    val id: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val url: String? = null,
    val deleteUrl: String? = null,
    val uploadDestination: UploadDestination,
    val timestamp: Long,
    val fileName: String,
    val fileSize: Long = 0,
    val albumId: String? = null,
    val tags: List<Tag> = emptyList()
)

enum class UploadDestination(val displayName: String) {
    IMGUR("Imgur"),
    S3("S3"),
    FTP("FTP"),
    SFTP("SFTP"),
    LOCAL("Local")
}
