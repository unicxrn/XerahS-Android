package com.xerahs.android.feature.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()

    suspend fun exportSettings(): String {
        val json = JsonObject()

        json.addProperty("defaultDestination", settingsRepository.getDefaultDestination().first().name)
        json.addProperty("overlayEnabled", settingsRepository.getOverlayEnabled().first())
        json.addProperty("themeMode", settingsRepository.getThemeMode().first().name)
        json.addProperty("fileNamingPattern", settingsRepository.getFileNamingPattern().first())

        val imgurConfig = settingsRepository.getImgurConfig()
        val imgur = JsonObject()
        imgur.addProperty("clientId", imgurConfig.clientId)
        imgur.addProperty("clientSecret", imgurConfig.clientSecret)
        imgur.addProperty("accessToken", imgurConfig.accessToken)
        imgur.addProperty("refreshToken", imgurConfig.refreshToken)
        imgur.addProperty("useAnonymous", imgurConfig.useAnonymous)
        json.add("imgur", imgur)

        val s3Config = settingsRepository.getS3Config()
        val s3 = JsonObject()
        s3.addProperty("accessKeyId", s3Config.accessKeyId)
        s3.addProperty("secretAccessKey", s3Config.secretAccessKey)
        s3.addProperty("region", s3Config.region)
        s3.addProperty("bucket", s3Config.bucket)
        s3.addProperty("endpoint", s3Config.endpoint)
        s3.addProperty("customUrl", s3Config.customUrl)
        s3.addProperty("prefix", s3Config.prefix)
        s3.addProperty("acl", s3Config.acl)
        s3.addProperty("usePathStyle", s3Config.usePathStyle)
        json.add("s3", s3)

        val ftpConfig = settingsRepository.getFtpConfig()
        val ftp = JsonObject()
        ftp.addProperty("host", ftpConfig.host)
        ftp.addProperty("port", ftpConfig.port)
        ftp.addProperty("username", ftpConfig.username)
        ftp.addProperty("password", ftpConfig.password)
        ftp.addProperty("remotePath", ftpConfig.remotePath)
        ftp.addProperty("useFtps", ftpConfig.useFtps)
        ftp.addProperty("usePassiveMode", ftpConfig.usePassiveMode)
        ftp.addProperty("httpUrl", ftpConfig.httpUrl)
        json.add("ftp", ftp)

        val sftpConfig = settingsRepository.getSftpConfig()
        val sftp = JsonObject()
        sftp.addProperty("host", sftpConfig.host)
        sftp.addProperty("port", sftpConfig.port)
        sftp.addProperty("username", sftpConfig.username)
        sftp.addProperty("password", sftpConfig.password)
        sftp.addProperty("keyPath", sftpConfig.keyPath)
        sftp.addProperty("keyPassphrase", sftpConfig.keyPassphrase)
        sftp.addProperty("remotePath", sftpConfig.remotePath)
        sftp.addProperty("httpUrl", sftpConfig.httpUrl)
        json.add("sftp", sftp)

        val customHttpConfig = settingsRepository.getCustomHttpConfig()
        val customHttp = JsonObject()
        customHttp.addProperty("url", customHttpConfig.url)
        customHttp.addProperty("method", customHttpConfig.method)
        customHttp.addProperty("responseUrlJsonPath", customHttpConfig.responseUrlJsonPath)
        customHttp.addProperty("formFieldName", customHttpConfig.formFieldName)
        val headersObj = JsonObject()
        customHttpConfig.headers.forEach { (k, v) -> headersObj.addProperty(k, v) }
        customHttp.add("headers", headersObj)
        json.add("customHttp", customHttp)

        json.addProperty("uploadFormat", settingsRepository.getUploadFormat().first().name)
        json.addProperty("stripExif", settingsRepository.getStripExif().first())
        json.addProperty("autoLockTimeout", settingsRepository.getAutoLockTimeout().first())

        return gson.toJson(json)
    }

    suspend fun importSettings(jsonString: String) {
        val json = gson.fromJson(jsonString, JsonObject::class.java)

        json.get("defaultDestination")?.asString?.let { name ->
            try {
                settingsRepository.setDefaultDestination(UploadDestination.valueOf(name))
            } catch (_: IllegalArgumentException) {}
        }

        json.get("overlayEnabled")?.asBoolean?.let {
            settingsRepository.setOverlayEnabled(it)
        }

        json.get("themeMode")?.asString?.let { name ->
            try {
                settingsRepository.setThemeMode(ThemeMode.valueOf(name))
            } catch (_: IllegalArgumentException) {}
        }

        json.get("fileNamingPattern")?.asString?.let {
            settingsRepository.setFileNamingPattern(it)
        }

        json.getAsJsonObject("imgur")?.let { imgur ->
            val current = settingsRepository.getImgurConfig()
            settingsRepository.saveImgurConfig(
                current.copy(
                    clientId = imgur.get("clientId")?.asString ?: current.clientId,
                    clientSecret = imgur.get("clientSecret")?.asString ?: current.clientSecret,
                    accessToken = imgur.get("accessToken")?.asString,
                    refreshToken = imgur.get("refreshToken")?.asString,
                    useAnonymous = imgur.get("useAnonymous")?.asBoolean ?: current.useAnonymous
                )
            )
        }

        json.getAsJsonObject("s3")?.let { s3 ->
            val current = settingsRepository.getS3Config()
            settingsRepository.saveS3Config(
                current.copy(
                    accessKeyId = s3.get("accessKeyId")?.asString ?: current.accessKeyId,
                    secretAccessKey = s3.get("secretAccessKey")?.asString ?: current.secretAccessKey,
                    region = s3.get("region")?.asString ?: current.region,
                    bucket = s3.get("bucket")?.asString ?: current.bucket,
                    endpoint = s3.get("endpoint")?.asString,
                    customUrl = s3.get("customUrl")?.asString,
                    prefix = s3.get("prefix")?.asString ?: current.prefix,
                    acl = s3.get("acl")?.asString ?: current.acl,
                    usePathStyle = s3.get("usePathStyle")?.asBoolean ?: current.usePathStyle
                )
            )
        }

        json.getAsJsonObject("ftp")?.let { ftp ->
            val current = settingsRepository.getFtpConfig()
            settingsRepository.saveFtpConfig(
                current.copy(
                    host = ftp.get("host")?.asString ?: current.host,
                    port = ftp.get("port")?.asInt ?: current.port,
                    username = ftp.get("username")?.asString ?: current.username,
                    password = ftp.get("password")?.asString ?: current.password,
                    remotePath = ftp.get("remotePath")?.asString ?: current.remotePath,
                    useFtps = ftp.get("useFtps")?.asBoolean ?: current.useFtps,
                    usePassiveMode = ftp.get("usePassiveMode")?.asBoolean ?: current.usePassiveMode,
                    httpUrl = ftp.get("httpUrl")?.asString ?: current.httpUrl
                )
            )
        }

        json.getAsJsonObject("sftp")?.let { sftp ->
            val current = settingsRepository.getSftpConfig()
            settingsRepository.saveSftpConfig(
                current.copy(
                    host = sftp.get("host")?.asString ?: current.host,
                    port = sftp.get("port")?.asInt ?: current.port,
                    username = sftp.get("username")?.asString ?: current.username,
                    password = sftp.get("password")?.asString ?: current.password,
                    keyPath = sftp.get("keyPath")?.asString,
                    keyPassphrase = sftp.get("keyPassphrase")?.asString,
                    remotePath = sftp.get("remotePath")?.asString ?: current.remotePath,
                    httpUrl = sftp.get("httpUrl")?.asString ?: current.httpUrl
                )
            )
        }

        json.getAsJsonObject("customHttp")?.let { ch ->
            val current = settingsRepository.getCustomHttpConfig()
            val headers = mutableMapOf<String, String>()
            ch.getAsJsonObject("headers")?.entrySet()?.forEach { (k, v) ->
                headers[k] = v.asString
            }
            settingsRepository.saveCustomHttpConfig(
                current.copy(
                    url = ch.get("url")?.asString ?: current.url,
                    method = ch.get("method")?.asString ?: current.method,
                    responseUrlJsonPath = ch.get("responseUrlJsonPath")?.asString ?: current.responseUrlJsonPath,
                    formFieldName = ch.get("formFieldName")?.asString ?: current.formFieldName,
                    headers = if (headers.isNotEmpty()) headers else current.headers
                )
            )
        }

        json.get("uploadFormat")?.asString?.let { name ->
            try {
                settingsRepository.setUploadFormat(ImageFormat.valueOf(name))
            } catch (_: IllegalArgumentException) {}
        }

        json.get("stripExif")?.asBoolean?.let {
            settingsRepository.setStripExif(it)
        }

        json.get("autoLockTimeout")?.asLong?.let {
            settingsRepository.setAutoLockTimeout(it)
        }
    }
}
