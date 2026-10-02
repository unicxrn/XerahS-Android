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
    val tags: List<Tag> = emptyList(),
    val fileHash: String? = null,
    // MIME type of the uploaded file; null/"image/*" for rows created before v0.5.
    val mimeType: String? = null,
    val remoteKey: String? = null,
    val profileId: String? = null
) {
    val isImage: Boolean
        get() = mimeType == null || com.xerahs.android.core.common.file.MimeTypes.isRasterImage(mimeType)
}

enum class UploadDestination(val displayName: String) {
    IMGUR("Imgur"),
    S3("S3"),
    FTP("FTP"),
    SFTP("SFTP"),
    CUSTOM_HTTP("Custom uploader"),
    LOCAL("Local"),
    NEXTCLOUD("Nextcloud"),
    IMMICH("Immich"),
    GITHUB_GIST("GitHub Gist")
}
