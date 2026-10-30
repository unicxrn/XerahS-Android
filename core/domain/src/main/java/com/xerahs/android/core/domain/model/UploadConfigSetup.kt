package com.xerahs.android.core.domain.model

import com.xerahs.android.core.domain.repository.SettingsRepository

/** True when every field the uploader needs to even try a request is filled in. */
fun UploadConfig.isConfiguredForUpload(): Boolean = when (this) {
    is UploadConfig.ImgurConfig -> clientId.isNotBlank() && (useAnonymous || !accessToken.isNullOrBlank())
    is UploadConfig.S3Config ->
        accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucket.isNotBlank() &&
            region.isNotBlank()
    is UploadConfig.FtpConfig -> host.isNotBlank()
    is UploadConfig.SftpConfig ->
        host.isNotBlank() && username.isNotBlank() &&
            (password.isNotEmpty() || !keyPath.isNullOrBlank())
    is UploadConfig.CustomUploaderConfig -> spec.requestURL.isNotBlank()
    is UploadConfig.NextcloudConfig ->
        serverUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank()
    is UploadConfig.ImmichConfig -> serverUrl.isNotBlank() && apiKey.isNotBlank()
    is UploadConfig.GistConfig -> token.isNotBlank()
}

/** Setup is done when the global config passes, or any profile for the destination does. */
fun isSetupComplete(global: UploadConfig?, profileConfigs: List<UploadConfig>): Boolean =
    global?.isConfiguredForUpload() == true || profileConfigs.any { it.isConfiguredForUpload() }

/** The global (non-profile) config for a destination, or null for Local which has none. */
suspend fun SettingsRepository.globalConfigFor(dest: UploadDestination): UploadConfig? = when (dest) {
    UploadDestination.LOCAL -> null
    UploadDestination.IMGUR -> getImgurConfig()
    UploadDestination.S3 -> getS3Config()
    UploadDestination.FTP -> getFtpConfig()
    UploadDestination.SFTP -> getSftpConfig()
    UploadDestination.CUSTOM_HTTP -> getCustomUploaderConfig()
    UploadDestination.NEXTCLOUD -> getNextcloudConfig()
    UploadDestination.IMMICH -> getImmichConfig()
    UploadDestination.GITHUB_GIST -> getGistConfig()
}
