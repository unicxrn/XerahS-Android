package com.xerahs.android.core.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.xerahs.android.core.domain.model.UploadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
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

    // Custom HTTP
    fun getCustomHttpConfig(): UploadConfig.CustomHttpConfig {
        val headersJson = prefs.getString("custom_http_headers", "") ?: ""
        val headers = if (headersJson.isNotEmpty()) {
            headersJson.split("\n").filter { it.contains("=") }.associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key to value
            }
        } else emptyMap()

        return UploadConfig.CustomHttpConfig(
            url = prefs.getString("custom_http_url", "") ?: "",
            method = prefs.getString("custom_http_method", "POST") ?: "POST",
            headers = headers,
            responseUrlJsonPath = prefs.getString("custom_http_json_path", "url") ?: "url",
            formFieldName = prefs.getString("custom_http_form_field", "file") ?: "file"
        )
    }

    fun saveCustomHttpConfig(config: UploadConfig.CustomHttpConfig) {
        val headersString = config.headers.entries.joinToString("\n") { "${it.key}=${it.value}" }
        prefs.edit().apply {
            putString("custom_http_url", config.url)
            putString("custom_http_method", config.method)
            putString("custom_http_headers", headersString)
            putString("custom_http_json_path", config.responseUrlJsonPath)
            putString("custom_http_form_field", config.formFieldName)
            apply()
        }
    }
}
