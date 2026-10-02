package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.sxcu.CustomDestinationType

/** Which destinations can receive which kinds of file. */
object DestinationCapabilities {
    /**
     * @param customTypes the custom uploader's destination types; null when unknown (treated as "accepts").
     */
    fun accepts(
        destination: UploadDestination,
        mimeType: String,
        customTypes: Set<CustomDestinationType>? = null,
    ): Boolean = when (destination) {
        UploadDestination.IMGUR -> MimeTypes.isRasterImage(mimeType)
        UploadDestination.CUSTOM_HTTP -> customTypes == null || when {
            MimeTypes.isRasterImage(mimeType) ->
                CustomDestinationType.IMAGE in customTypes || CustomDestinationType.FILE in customTypes
            MimeTypes.isText(mimeType) ->
                CustomDestinationType.TEXT in customTypes || CustomDestinationType.FILE in customTypes
            else -> CustomDestinationType.FILE in customTypes
        }
        UploadDestination.IMMICH -> MimeTypes.isRasterImage(mimeType) || mimeType.startsWith("video/")
        UploadDestination.GITHUB_GIST -> MimeTypes.isText(mimeType)
        UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP,
        UploadDestination.LOCAL, UploadDestination.NEXTCLOUD -> true
    }

    fun acceptsAll(
        destination: UploadDestination,
        mimeTypes: List<String>,
        customTypes: Set<CustomDestinationType>? = null,
    ): Boolean = mimeTypes.all { accepts(destination, it, customTypes) }
}
