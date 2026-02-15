package com.xerahs.android.feature.s3explorer.model

data class S3Object(
    val key: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val etag: String = "",
    val storageClass: String = ""
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    val isImage: Boolean get() = extension in setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif"
    )
}

data class S3Folder(
    val prefix: String,
    val name: String
)

data class S3ListResult(
    val objects: List<S3Object>,
    val folders: List<S3Folder>,
    val isTruncated: Boolean,
    val nextContinuationToken: String?
)
