package com.xerahs.android.ui.navigation

import com.xerahs.android.core.domain.model.UploadDestination

/** The settings screen where a destination is configured, or null when it needs no setup. */
fun setupRouteFor(destination: UploadDestination): String? = when (destination) {
    UploadDestination.IMGUR, UploadDestination.LOCAL -> null
    UploadDestination.S3 -> Screen.S3Config.route
    UploadDestination.FTP, UploadDestination.SFTP -> Screen.FtpConfig.route
    UploadDestination.CUSTOM_HTTP -> Screen.UploaderImport.createRoute()
    UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST ->
        Screen.NativeDestinationConfig.createRoute(destination)
}
