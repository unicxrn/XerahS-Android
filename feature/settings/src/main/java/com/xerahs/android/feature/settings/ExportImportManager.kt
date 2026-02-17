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

    suspend fun parseImportPreview(jsonString: String): ImportPreview {
        val json = gson.fromJson(jsonString, JsonObject::class.java)
        val sections = mutableListOf<ImportSection>()

        // General section
        val generalFields = mutableListOf<ImportField>()
        fun addGeneralField(key: String, label: String, currentValue: String, importedValue: String?) {
            if (importedValue != null) {
                generalFields.add(ImportField(key, label, currentValue, importedValue, currentValue != importedValue))
            }
        }

        addGeneralField("defaultDestination", "Default Destination",
            settingsRepository.getDefaultDestination().first().name,
            json.get("defaultDestination")?.asString)
        addGeneralField("themeMode", "Theme Mode",
            settingsRepository.getThemeMode().first().name,
            json.get("themeMode")?.asString)
        addGeneralField("fileNamingPattern", "File Naming Pattern",
            settingsRepository.getFileNamingPattern().first(),
            json.get("fileNamingPattern")?.asString)
        addGeneralField("uploadFormat", "Upload Format",
            settingsRepository.getUploadFormat().first().name,
            json.get("uploadFormat")?.asString)
        addGeneralField("stripExif", "Strip EXIF",
            settingsRepository.getStripExif().first().toString(),
            json.get("stripExif")?.asBoolean?.toString())

        if (generalFields.isNotEmpty()) {
            sections.add(ImportSection("General", generalFields))
        }

        // Imgur section
        json.getAsJsonObject("imgur")?.let { imgur ->
            val current = settingsRepository.getImgurConfig()
            val fields = mutableListOf<ImportField>()
            fun addField(key: String, label: String, cur: String, imp: String?) {
                if (imp != null) fields.add(ImportField(key, label, cur, imp, cur != imp))
            }
            addField("imgur.clientId", "Client ID", current.clientId, imgur.get("clientId")?.asString)
            addField("imgur.clientSecret", "Client Secret", maskSecret(current.clientSecret), maskSecret(imgur.get("clientSecret")?.asString ?: ""))
            addField("imgur.useAnonymous", "Anonymous Upload", current.useAnonymous.toString(), imgur.get("useAnonymous")?.asBoolean?.toString())
            if (fields.isNotEmpty()) sections.add(ImportSection("Imgur", fields))
        }

        // S3 section
        json.getAsJsonObject("s3")?.let { s3 ->
            val current = settingsRepository.getS3Config()
            val fields = mutableListOf<ImportField>()
            fun addField(key: String, label: String, cur: String, imp: String?) {
                if (imp != null) fields.add(ImportField(key, label, cur, imp, cur != imp))
            }
            addField("s3.accessKeyId", "Access Key ID", maskSecret(current.accessKeyId), maskSecret(s3.get("accessKeyId")?.asString ?: ""))
            addField("s3.region", "Region", current.region, s3.get("region")?.asString)
            addField("s3.bucket", "Bucket", current.bucket, s3.get("bucket")?.asString)
            addField("s3.endpoint", "Endpoint", current.endpoint ?: "", s3.get("endpoint")?.asString ?: "")
            addField("s3.prefix", "Prefix", current.prefix, s3.get("prefix")?.asString)
            addField("s3.usePathStyle", "Path Style", current.usePathStyle.toString(), s3.get("usePathStyle")?.asBoolean?.toString())
            if (fields.isNotEmpty()) sections.add(ImportSection("Amazon S3", fields))
        }

        // FTP section
        json.getAsJsonObject("ftp")?.let { ftp ->
            val current = settingsRepository.getFtpConfig()
            val fields = mutableListOf<ImportField>()
            fun addField(key: String, label: String, cur: String, imp: String?) {
                if (imp != null) fields.add(ImportField(key, label, cur, imp, cur != imp))
            }
            addField("ftp.host", "Host", current.host, ftp.get("host")?.asString)
            addField("ftp.port", "Port", current.port.toString(), ftp.get("port")?.asInt?.toString())
            addField("ftp.username", "Username", current.username, ftp.get("username")?.asString)
            addField("ftp.remotePath", "Remote Path", current.remotePath, ftp.get("remotePath")?.asString)
            addField("ftp.httpUrl", "HTTP URL", current.httpUrl, ftp.get("httpUrl")?.asString)
            if (fields.isNotEmpty()) sections.add(ImportSection("FTP", fields))
        }

        // SFTP section
        json.getAsJsonObject("sftp")?.let { sftp ->
            val current = settingsRepository.getSftpConfig()
            val fields = mutableListOf<ImportField>()
            fun addField(key: String, label: String, cur: String, imp: String?) {
                if (imp != null) fields.add(ImportField(key, label, cur, imp, cur != imp))
            }
            addField("sftp.host", "Host", current.host, sftp.get("host")?.asString)
            addField("sftp.port", "Port", current.port.toString(), sftp.get("port")?.asInt?.toString())
            addField("sftp.username", "Username", current.username, sftp.get("username")?.asString)
            addField("sftp.remotePath", "Remote Path", current.remotePath, sftp.get("remotePath")?.asString)
            addField("sftp.httpUrl", "HTTP URL", current.httpUrl, sftp.get("httpUrl")?.asString)
            if (fields.isNotEmpty()) sections.add(ImportSection("SFTP", fields))
        }

        // Custom HTTP section
        json.getAsJsonObject("customHttp")?.let { ch ->
            val current = settingsRepository.getCustomHttpConfig()
            val fields = mutableListOf<ImportField>()
            fun addField(key: String, label: String, cur: String, imp: String?) {
                if (imp != null) fields.add(ImportField(key, label, cur, imp, cur != imp))
            }
            addField("customHttp.url", "URL", current.url, ch.get("url")?.asString)
            addField("customHttp.method", "Method", current.method, ch.get("method")?.asString)
            addField("customHttp.responseUrlJsonPath", "JSON Path", current.responseUrlJsonPath, ch.get("responseUrlJsonPath")?.asString)
            addField("customHttp.formFieldName", "Form Field", current.formFieldName, ch.get("formFieldName")?.asString)
            if (fields.isNotEmpty()) sections.add(ImportSection("Custom HTTP", fields))
        }

        return ImportPreview(sections)
    }

    suspend fun applyResolvedImport(jsonString: String, preview: ImportPreview) {
        val json = gson.fromJson(jsonString, JsonObject::class.java)
        val accepted = preview.sections.flatMap { it.fields }
            .filter { it.resolution == FieldResolution.USE_IMPORTED }
            .map { it.key }
            .toSet()

        if (accepted.isEmpty()) return

        // General
        if ("defaultDestination" in accepted) {
            json.get("defaultDestination")?.asString?.let { name ->
                try { settingsRepository.setDefaultDestination(UploadDestination.valueOf(name)) } catch (_: Exception) {}
            }
        }
        if ("themeMode" in accepted) {
            json.get("themeMode")?.asString?.let { name ->
                try { settingsRepository.setThemeMode(ThemeMode.valueOf(name)) } catch (_: Exception) {}
            }
        }
        if ("fileNamingPattern" in accepted) {
            json.get("fileNamingPattern")?.asString?.let { settingsRepository.setFileNamingPattern(it) }
        }
        if ("uploadFormat" in accepted) {
            json.get("uploadFormat")?.asString?.let { name ->
                try { settingsRepository.setUploadFormat(ImageFormat.valueOf(name)) } catch (_: Exception) {}
            }
        }
        if ("stripExif" in accepted) {
            json.get("stripExif")?.asBoolean?.let { settingsRepository.setStripExif(it) }
        }

        // Imgur
        val imgurKeys = accepted.filter { it.startsWith("imgur.") }
        if (imgurKeys.isNotEmpty()) {
            json.getAsJsonObject("imgur")?.let { imgur ->
                val current = settingsRepository.getImgurConfig()
                settingsRepository.saveImgurConfig(current.copy(
                    clientId = if ("imgur.clientId" in accepted) imgur.get("clientId")?.asString ?: current.clientId else current.clientId,
                    clientSecret = if ("imgur.clientSecret" in accepted) imgur.get("clientSecret")?.asString ?: current.clientSecret else current.clientSecret,
                    useAnonymous = if ("imgur.useAnonymous" in accepted) imgur.get("useAnonymous")?.asBoolean ?: current.useAnonymous else current.useAnonymous
                ))
            }
        }

        // S3
        val s3Keys = accepted.filter { it.startsWith("s3.") }
        if (s3Keys.isNotEmpty()) {
            json.getAsJsonObject("s3")?.let { s3 ->
                val current = settingsRepository.getS3Config()
                settingsRepository.saveS3Config(current.copy(
                    accessKeyId = if ("s3.accessKeyId" in accepted) s3.get("accessKeyId")?.asString ?: current.accessKeyId else current.accessKeyId,
                    secretAccessKey = if ("s3.secretAccessKey" in accepted) s3.get("secretAccessKey")?.asString ?: current.secretAccessKey else current.secretAccessKey,
                    region = if ("s3.region" in accepted) s3.get("region")?.asString ?: current.region else current.region,
                    bucket = if ("s3.bucket" in accepted) s3.get("bucket")?.asString ?: current.bucket else current.bucket,
                    endpoint = if ("s3.endpoint" in accepted) s3.get("endpoint")?.asString else current.endpoint,
                    prefix = if ("s3.prefix" in accepted) s3.get("prefix")?.asString ?: current.prefix else current.prefix,
                    usePathStyle = if ("s3.usePathStyle" in accepted) s3.get("usePathStyle")?.asBoolean ?: current.usePathStyle else current.usePathStyle
                ))
            }
        }

        // FTP
        val ftpKeys = accepted.filter { it.startsWith("ftp.") }
        if (ftpKeys.isNotEmpty()) {
            json.getAsJsonObject("ftp")?.let { ftp ->
                val current = settingsRepository.getFtpConfig()
                settingsRepository.saveFtpConfig(current.copy(
                    host = if ("ftp.host" in accepted) ftp.get("host")?.asString ?: current.host else current.host,
                    port = if ("ftp.port" in accepted) ftp.get("port")?.asInt ?: current.port else current.port,
                    username = if ("ftp.username" in accepted) ftp.get("username")?.asString ?: current.username else current.username,
                    password = if ("ftp.password" in accepted) ftp.get("password")?.asString ?: current.password else current.password,
                    remotePath = if ("ftp.remotePath" in accepted) ftp.get("remotePath")?.asString ?: current.remotePath else current.remotePath,
                    httpUrl = if ("ftp.httpUrl" in accepted) ftp.get("httpUrl")?.asString ?: current.httpUrl else current.httpUrl
                ))
            }
        }

        // SFTP
        val sftpKeys = accepted.filter { it.startsWith("sftp.") }
        if (sftpKeys.isNotEmpty()) {
            json.getAsJsonObject("sftp")?.let { sftp ->
                val current = settingsRepository.getSftpConfig()
                settingsRepository.saveSftpConfig(current.copy(
                    host = if ("sftp.host" in accepted) sftp.get("host")?.asString ?: current.host else current.host,
                    port = if ("sftp.port" in accepted) sftp.get("port")?.asInt ?: current.port else current.port,
                    username = if ("sftp.username" in accepted) sftp.get("username")?.asString ?: current.username else current.username,
                    password = if ("sftp.password" in accepted) sftp.get("password")?.asString ?: current.password else current.password,
                    remotePath = if ("sftp.remotePath" in accepted) sftp.get("remotePath")?.asString ?: current.remotePath else current.remotePath,
                    httpUrl = if ("sftp.httpUrl" in accepted) sftp.get("httpUrl")?.asString ?: current.httpUrl else current.httpUrl
                ))
            }
        }

        // Custom HTTP
        val chKeys = accepted.filter { it.startsWith("customHttp.") }
        if (chKeys.isNotEmpty()) {
            json.getAsJsonObject("customHttp")?.let { ch ->
                val current = settingsRepository.getCustomHttpConfig()
                val headers = mutableMapOf<String, String>()
                ch.getAsJsonObject("headers")?.entrySet()?.forEach { (k, v) -> headers[k] = v.asString }
                settingsRepository.saveCustomHttpConfig(current.copy(
                    url = if ("customHttp.url" in accepted) ch.get("url")?.asString ?: current.url else current.url,
                    method = if ("customHttp.method" in accepted) ch.get("method")?.asString ?: current.method else current.method,
                    responseUrlJsonPath = if ("customHttp.responseUrlJsonPath" in accepted) ch.get("responseUrlJsonPath")?.asString ?: current.responseUrlJsonPath else current.responseUrlJsonPath,
                    formFieldName = if ("customHttp.formFieldName" in accepted) ch.get("formFieldName")?.asString ?: current.formFieldName else current.formFieldName,
                    headers = if (headers.isNotEmpty()) headers else current.headers
                ))
            }
        }
    }

    private fun maskSecret(value: String): String {
        return if (value.length > 4) "${"*".repeat(value.length - 4)}${value.takeLast(4)}" else value
    }
}
