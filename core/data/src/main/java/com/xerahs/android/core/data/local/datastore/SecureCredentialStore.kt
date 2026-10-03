package com.xerahs.android.core.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.xerahs.android.core.common.sxcu.LegacyCustomHttp
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (_: Exception) {
            // Keystore keys invalidated (e.g. after uninstall/reinstall), clear and recreate
            context.getSharedPreferences("xerahs_secure_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
            val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/xerahs_secure_prefs.xml")
            prefsFile.delete()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "xerahs_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Imgur
    fun getImgurConfig(): UploadConfig.ImgurConfig = UploadConfig.ImgurConfig(
        clientId = prefs.getString("imgur_client_id", "") ?: "",
        clientSecret = prefs.getString("imgur_client_secret", "") ?: "",
        accessToken = prefs.getString("imgur_access_token", null),
        refreshToken = prefs.getString("imgur_refresh_token", null),
        useAnonymous = prefs.getBoolean("imgur_use_anonymous", true)
    )

    fun saveImgurConfig(config: UploadConfig.ImgurConfig) {
        prefs.edit().apply {
            putString("imgur_client_id", config.clientId)
            putString("imgur_client_secret", config.clientSecret)
            putString("imgur_access_token", config.accessToken)
            putString("imgur_refresh_token", config.refreshToken)
            putBoolean("imgur_use_anonymous", config.useAnonymous)
            apply()
        }
    }

    // S3
    fun getS3Config(): UploadConfig.S3Config = UploadConfig.S3Config(
        accessKeyId = prefs.getString("s3_access_key_id", "") ?: "",
        secretAccessKey = prefs.getString("s3_secret_access_key", "") ?: "",
        region = prefs.getString("s3_region", "us-east-1") ?: "us-east-1",
        bucket = prefs.getString("s3_bucket", "") ?: "",
        endpoint = prefs.getString("s3_endpoint", null),
        customUrl = prefs.getString("s3_custom_url", null),
        prefix = prefs.getString("s3_prefix", "") ?: "",
        acl = prefs.getString("s3_acl", "") ?: "",
        usePathStyle = prefs.getBoolean("s3_use_path_style", false)
    )

    fun saveS3Config(config: UploadConfig.S3Config) {
        prefs.edit().apply {
            putString("s3_access_key_id", config.accessKeyId)
            putString("s3_secret_access_key", config.secretAccessKey)
            putString("s3_region", config.region)
            putString("s3_bucket", config.bucket)
            putString("s3_endpoint", config.endpoint)
            putString("s3_custom_url", config.customUrl)
            putString("s3_prefix", config.prefix)
            putString("s3_acl", config.acl)
            putBoolean("s3_use_path_style", config.usePathStyle)
            apply()
        }
    }

    // FTP
    fun getFtpConfig(): UploadConfig.FtpConfig = UploadConfig.FtpConfig(
        host = prefs.getString("ftp_host", "") ?: "",
        port = prefs.getInt("ftp_port", 21),
        username = prefs.getString("ftp_username", "") ?: "",
        password = prefs.getString("ftp_password", "") ?: "",
        remotePath = prefs.getString("ftp_remote_path", "/") ?: "/",
        useFtps = prefs.getBoolean("ftp_use_ftps", false),
        usePassiveMode = prefs.getBoolean("ftp_use_passive", true),
        httpUrl = prefs.getString("ftp_http_url", "") ?: ""
    )

    fun saveFtpConfig(config: UploadConfig.FtpConfig) {
        prefs.edit().apply {
            putString("ftp_host", config.host)
            putInt("ftp_port", config.port)
            putString("ftp_username", config.username)
            putString("ftp_password", config.password)
            putString("ftp_remote_path", config.remotePath)
            putBoolean("ftp_use_ftps", config.useFtps)
            putBoolean("ftp_use_passive", config.usePassiveMode)
            putString("ftp_http_url", config.httpUrl)
            apply()
        }
    }

    // SFTP
    fun getSftpConfig(): UploadConfig.SftpConfig = UploadConfig.SftpConfig(
        host = prefs.getString("sftp_host", "") ?: "",
        port = prefs.getInt("sftp_port", 22),
        username = prefs.getString("sftp_username", "") ?: "",
        password = prefs.getString("sftp_password", "") ?: "",
        keyPath = prefs.getString("sftp_key_path", null),
        keyPassphrase = prefs.getString("sftp_key_passphrase", null),
        remotePath = prefs.getString("sftp_remote_path", "/") ?: "/",
        httpUrl = prefs.getString("sftp_http_url", "") ?: ""
    )

    fun saveSftpConfig(config: UploadConfig.SftpConfig) {
        prefs.edit().apply {
            putString("sftp_host", config.host)
            putInt("sftp_port", config.port)
            putString("sftp_username", config.username)
            putString("sftp_password", config.password)
            putString("sftp_key_path", config.keyPath)
            putString("sftp_key_passphrase", config.keyPassphrase)
            putString("sftp_remote_path", config.remotePath)
            putString("sftp_http_url", config.httpUrl)
            apply()
        }
    }

    // Custom uploader (.sxcu JSON). Prefix "" = global config, "profile_<id>_" = profile config.
    fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig = readCustomUploader("")

    fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig) {
        prefs.edit().putString("custom_uploader_sxcu", SxcuWriter.write(config.spec)).apply()
    }

    private fun readCustomUploader(p: String): UploadConfig.CustomUploaderConfig {
        prefs.getString("${p}custom_uploader_sxcu", null)?.let { stored ->
            SxcuParser.parse(stored).getOrNull()?.let { return UploadConfig.CustomUploaderConfig(it) }
        }
        val legacyUrl = prefs.getString("${p}custom_http_url", null)
        if (legacyUrl.isNullOrEmpty()) return UploadConfig.CustomUploaderConfig()

        // One-time in-place migration of the pre-0.5 Custom HTTP keys.
        val spec = LegacyCustomHttp.toSpec(
            url = legacyUrl,
            method = prefs.getString("${p}custom_http_method", "POST") ?: "POST",
            headers = LegacyCustomHttp.parseHeaderLines(prefs.getString("${p}custom_http_headers", "") ?: ""),
            responseUrlJsonPath = prefs.getString("${p}custom_http_json_path", "url") ?: "url",
            formFieldName = prefs.getString("${p}custom_http_form_field", "file") ?: "file",
        )
        prefs.edit().apply {
            putString("${p}custom_uploader_sxcu", SxcuWriter.write(spec))
            listOf("url", "method", "headers", "json_path", "form_field")
                .forEach { remove("${p}custom_http_$it") }
            apply()
        }
        return UploadConfig.CustomUploaderConfig(spec)
    }

    // Nextcloud / Immich / GitHub Gist. Prefix "" = global, "profile_<id>_" = profile.
    fun getNextcloudConfig() = readNextcloud("")
    fun saveNextcloudConfig(config: UploadConfig.NextcloudConfig) = prefs.edit().apply { writeNextcloud("", config) }.apply()
    fun getImmichConfig() = readImmich("")
    fun saveImmichConfig(config: UploadConfig.ImmichConfig) = prefs.edit().apply { writeImmich("", config) }.apply()
    fun getGistConfig() = readGist("")
    fun saveGistConfig(config: UploadConfig.GistConfig) = prefs.edit().apply { writeGist("", config) }.apply()

    private fun readNextcloud(p: String) = UploadConfig.NextcloudConfig(
        serverUrl = prefs.getString("${p}nextcloud_server_url", "") ?: "",
        username = prefs.getString("${p}nextcloud_username", "") ?: "",
        appPassword = prefs.getString("${p}nextcloud_app_password", "") ?: "",
        folder = prefs.getString("${p}nextcloud_folder", "XerahS") ?: "XerahS",
        publicShare = prefs.getBoolean("${p}nextcloud_public_share", true)
    )

    private fun SharedPreferences.Editor.writeNextcloud(p: String, c: UploadConfig.NextcloudConfig) {
        putString("${p}nextcloud_server_url", c.serverUrl)
        putString("${p}nextcloud_username", c.username)
        putString("${p}nextcloud_app_password", c.appPassword)
        putString("${p}nextcloud_folder", c.folder)
        putBoolean("${p}nextcloud_public_share", c.publicShare)
    }

    private fun readImmich(p: String) = UploadConfig.ImmichConfig(
        serverUrl = prefs.getString("${p}immich_server_url", "") ?: "",
        apiKey = prefs.getString("${p}immich_api_key", "") ?: "",
        createShareLink = prefs.getBoolean("${p}immich_share_link", true)
    )

    private fun SharedPreferences.Editor.writeImmich(p: String, c: UploadConfig.ImmichConfig) {
        putString("${p}immich_server_url", c.serverUrl)
        putString("${p}immich_api_key", c.apiKey)
        putBoolean("${p}immich_share_link", c.createShareLink)
    }

    private fun readGist(p: String) = UploadConfig.GistConfig(
        token = prefs.getString("${p}gist_token", "") ?: "",
        isPublic = prefs.getBoolean("${p}gist_public", false)
    )

    private fun SharedPreferences.Editor.writeGist(p: String, c: UploadConfig.GistConfig) {
        putString("${p}gist_token", c.token)
        putBoolean("${p}gist_public", c.isPublic)
    }

    // Profile-specific configs (keyed by profile ID)
    fun getProfileConfig(profileId: String, destination: UploadDestination): UploadConfig {
        val p = "profile_${profileId}_"
        return when (destination) {
            UploadDestination.IMGUR -> UploadConfig.ImgurConfig(
                clientId = prefs.getString("${p}imgur_client_id", "") ?: "",
                clientSecret = prefs.getString("${p}imgur_client_secret", "") ?: "",
                accessToken = prefs.getString("${p}imgur_access_token", null),
                refreshToken = prefs.getString("${p}imgur_refresh_token", null),
                useAnonymous = prefs.getBoolean("${p}imgur_use_anonymous", true)
            )
            UploadDestination.S3 -> UploadConfig.S3Config(
                accessKeyId = prefs.getString("${p}s3_access_key_id", "") ?: "",
                secretAccessKey = prefs.getString("${p}s3_secret_access_key", "") ?: "",
                region = prefs.getString("${p}s3_region", "us-east-1") ?: "us-east-1",
                bucket = prefs.getString("${p}s3_bucket", "") ?: "",
                endpoint = prefs.getString("${p}s3_endpoint", null),
                customUrl = prefs.getString("${p}s3_custom_url", null),
                prefix = prefs.getString("${p}s3_prefix", "") ?: "",
                acl = prefs.getString("${p}s3_acl", "") ?: "",
                usePathStyle = prefs.getBoolean("${p}s3_use_path_style", false)
            )
            UploadDestination.FTP -> UploadConfig.FtpConfig(
                host = prefs.getString("${p}ftp_host", "") ?: "",
                port = prefs.getInt("${p}ftp_port", 21),
                username = prefs.getString("${p}ftp_username", "") ?: "",
                password = prefs.getString("${p}ftp_password", "") ?: "",
                remotePath = prefs.getString("${p}ftp_remote_path", "/") ?: "/",
                useFtps = prefs.getBoolean("${p}ftp_use_ftps", false),
                usePassiveMode = prefs.getBoolean("${p}ftp_use_passive", true),
                httpUrl = prefs.getString("${p}ftp_http_url", "") ?: ""
            )
            UploadDestination.SFTP -> UploadConfig.SftpConfig(
                host = prefs.getString("${p}sftp_host", "") ?: "",
                port = prefs.getInt("${p}sftp_port", 22),
                username = prefs.getString("${p}sftp_username", "") ?: "",
                password = prefs.getString("${p}sftp_password", "") ?: "",
                keyPath = prefs.getString("${p}sftp_key_path", null),
                keyPassphrase = prefs.getString("${p}sftp_key_passphrase", null),
                remotePath = prefs.getString("${p}sftp_remote_path", "/") ?: "/",
                httpUrl = prefs.getString("${p}sftp_http_url", "") ?: ""
            )
            UploadDestination.CUSTOM_HTTP -> readCustomUploader(p)
            UploadDestination.LOCAL -> UploadConfig.S3Config() // Placeholder
            UploadDestination.NEXTCLOUD -> readNextcloud(p)
            UploadDestination.IMMICH -> readImmich(p)
            UploadDestination.GITHUB_GIST -> readGist(p)
        }
    }

    fun saveProfileConfig(profileId: String, config: UploadConfig) {
        val p = "profile_${profileId}_"
        prefs.edit().apply {
            when (config) {
                is UploadConfig.ImgurConfig -> {
                    putString("${p}imgur_client_id", config.clientId)
                    putString("${p}imgur_client_secret", config.clientSecret)
                    putString("${p}imgur_access_token", config.accessToken)
                    putString("${p}imgur_refresh_token", config.refreshToken)
                    putBoolean("${p}imgur_use_anonymous", config.useAnonymous)
                }
                is UploadConfig.S3Config -> {
                    putString("${p}s3_access_key_id", config.accessKeyId)
                    putString("${p}s3_secret_access_key", config.secretAccessKey)
                    putString("${p}s3_region", config.region)
                    putString("${p}s3_bucket", config.bucket)
                    putString("${p}s3_endpoint", config.endpoint)
                    putString("${p}s3_custom_url", config.customUrl)
                    putString("${p}s3_prefix", config.prefix)
                    putString("${p}s3_acl", config.acl)
                    putBoolean("${p}s3_use_path_style", config.usePathStyle)
                }
                is UploadConfig.FtpConfig -> {
                    putString("${p}ftp_host", config.host)
                    putInt("${p}ftp_port", config.port)
                    putString("${p}ftp_username", config.username)
                    putString("${p}ftp_password", config.password)
                    putString("${p}ftp_remote_path", config.remotePath)
                    putBoolean("${p}ftp_use_ftps", config.useFtps)
                    putBoolean("${p}ftp_use_passive", config.usePassiveMode)
                    putString("${p}ftp_http_url", config.httpUrl)
                }
                is UploadConfig.SftpConfig -> {
                    putString("${p}sftp_host", config.host)
                    putInt("${p}sftp_port", config.port)
                    putString("${p}sftp_username", config.username)
                    putString("${p}sftp_password", config.password)
                    putString("${p}sftp_key_path", config.keyPath)
                    putString("${p}sftp_key_passphrase", config.keyPassphrase)
                    putString("${p}sftp_remote_path", config.remotePath)
                    putString("${p}sftp_http_url", config.httpUrl)
                }
                is UploadConfig.CustomUploaderConfig -> {
                    putString("${p}custom_uploader_sxcu", SxcuWriter.write(config.spec))
                }
                is UploadConfig.NextcloudConfig -> writeNextcloud(p, config)
                is UploadConfig.ImmichConfig -> writeImmich(p, config)
                is UploadConfig.GistConfig -> writeGist(p, config)
            }
            apply()
        }
    }

    fun deleteProfileConfig(profileId: String) {
        val p = "profile_${profileId}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(p) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
