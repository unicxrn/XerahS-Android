package com.xerahs.android.core.domain.model

sealed class UploadConfig {
    data class ImgurConfig(
        val clientId: String = "",
        val clientSecret: String = "",
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val useAnonymous: Boolean = true
    ) : UploadConfig()

    data class S3Config(
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val region: String = "us-east-1",
        val bucket: String = "",
        val endpoint: String? = null,
        val customUrl: String? = null,
        val prefix: String = "",
        val acl: String = "",
        val usePathStyle: Boolean = false
    ) : UploadConfig()

    data class FtpConfig(
        val host: String = "",
        val port: Int = 21,
        val username: String = "",
        val password: String = "",
        val remotePath: String = "/",
        val useFtps: Boolean = false,
        val usePassiveMode: Boolean = true,
        val httpUrl: String = ""
    ) : UploadConfig()

    data class SftpConfig(
        val host: String = "",
        val port: Int = 22,
        val username: String = "",
        val password: String = "",
        val keyPath: String? = null,
        val keyPassphrase: String? = null,
        val remotePath: String = "/",
        val httpUrl: String = ""
    ) : UploadConfig()
}
