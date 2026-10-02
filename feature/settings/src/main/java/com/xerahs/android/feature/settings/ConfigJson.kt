package com.xerahs.android.feature.settings

import com.google.gson.JsonObject
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination

// JSON form of every UploadConfig, used for profiles in backups.
object ConfigJson {
    fun toJson(config: UploadConfig): JsonObject = JsonObject().apply {
        when (config) {
            is UploadConfig.ImgurConfig -> {
                addProperty("clientId", config.clientId); addProperty("clientSecret", config.clientSecret)
                addProperty("accessToken", config.accessToken); addProperty("refreshToken", config.refreshToken)
                addProperty("useAnonymous", config.useAnonymous)
            }
            is UploadConfig.S3Config -> {
                addProperty("accessKeyId", config.accessKeyId); addProperty("secretAccessKey", config.secretAccessKey)
                addProperty("region", config.region); addProperty("bucket", config.bucket)
                addProperty("endpoint", config.endpoint); addProperty("customUrl", config.customUrl)
                addProperty("prefix", config.prefix); addProperty("acl", config.acl)
                addProperty("usePathStyle", config.usePathStyle)
            }
            is UploadConfig.FtpConfig -> {
                addProperty("host", config.host); addProperty("port", config.port)
                addProperty("username", config.username); addProperty("password", config.password)
                addProperty("remotePath", config.remotePath); addProperty("useFtps", config.useFtps)
                addProperty("usePassiveMode", config.usePassiveMode); addProperty("httpUrl", config.httpUrl)
            }
            is UploadConfig.SftpConfig -> {
                addProperty("host", config.host); addProperty("port", config.port)
                addProperty("username", config.username); addProperty("password", config.password)
                addProperty("keyPath", config.keyPath); addProperty("keyPassphrase", config.keyPassphrase)
                addProperty("remotePath", config.remotePath); addProperty("httpUrl", config.httpUrl)
            }
            is UploadConfig.CustomUploaderConfig -> addProperty("sxcu", SxcuWriter.write(config.spec))
            is UploadConfig.NextcloudConfig -> {
                addProperty("serverUrl", config.serverUrl); addProperty("username", config.username)
                addProperty("appPassword", config.appPassword); addProperty("folder", config.folder)
                addProperty("publicShare", config.publicShare)
            }
            is UploadConfig.ImmichConfig -> {
                addProperty("serverUrl", config.serverUrl); addProperty("apiKey", config.apiKey)
                addProperty("createShareLink", config.createShareLink)
            }
            is UploadConfig.GistConfig -> { addProperty("token", config.token); addProperty("isPublic", config.isPublic) }
        }
    }

    fun fromJson(destination: UploadDestination, j: JsonObject): UploadConfig? {
        fun s(k: String) = j.get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        fun ns(k: String) = j.get(k)?.takeIf { it.isJsonPrimitive }?.asString
        fun b(k: String, d: Boolean) = j.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: d
        fun i(k: String, d: Int) = j.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: d
        return when (destination) {
            UploadDestination.IMGUR -> UploadConfig.ImgurConfig(s("clientId"), s("clientSecret"), ns("accessToken"), ns("refreshToken"), b("useAnonymous", true))
            UploadDestination.S3 -> UploadConfig.S3Config(s("accessKeyId"), s("secretAccessKey"), ns("region") ?: "us-east-1", s("bucket"), ns("endpoint"), ns("customUrl"), s("prefix"), s("acl"), b("usePathStyle", false))
            UploadDestination.FTP -> UploadConfig.FtpConfig(s("host"), i("port", 21), s("username"), s("password"), ns("remotePath") ?: "/", b("useFtps", false), b("usePassiveMode", true), s("httpUrl"))
            UploadDestination.SFTP -> UploadConfig.SftpConfig(s("host"), i("port", 22), s("username"), s("password"), ns("keyPath"), ns("keyPassphrase"), ns("remotePath") ?: "/", s("httpUrl"))
            UploadDestination.CUSTOM_HTTP -> ns("sxcu")?.let { SxcuParser.parse(it).getOrNull() }?.let { UploadConfig.CustomUploaderConfig(it) }
            UploadDestination.NEXTCLOUD -> UploadConfig.NextcloudConfig(s("serverUrl"), s("username"), s("appPassword"), ns("folder") ?: "XerahS", b("publicShare", true))
            UploadDestination.IMMICH -> UploadConfig.ImmichConfig(s("serverUrl"), s("apiKey"), b("createShareLink", true))
            UploadDestination.GITHUB_GIST -> UploadConfig.GistConfig(s("token"), b("isPublic", false))
            UploadDestination.LOCAL -> null
        }
    }
}
