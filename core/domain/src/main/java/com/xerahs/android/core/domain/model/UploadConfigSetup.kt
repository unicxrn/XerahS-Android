package com.xerahs.android.core.domain.model

/** True when every field the uploader needs to even try a request is filled in. */
fun UploadConfig.isConfiguredForUpload(): Boolean = when (this) {
    is UploadConfig.ImgurConfig -> true
    is UploadConfig.S3Config ->
        accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucket.isNotBlank()
    is UploadConfig.FtpConfig -> host.isNotBlank() && username.isNotBlank()
    is UploadConfig.SftpConfig ->
        host.isNotBlank() && username.isNotBlank() &&
            (password.isNotEmpty() || !keyPath.isNullOrBlank())
    is UploadConfig.CustomUploaderConfig -> spec.requestURL.isNotBlank()
    is UploadConfig.NextcloudConfig ->
        serverUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank()
    is UploadConfig.ImmichConfig -> serverUrl.isNotBlank() && apiKey.isNotBlank()
    is UploadConfig.GistConfig -> token.isNotBlank()
}
