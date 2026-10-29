package com.xerahs.android.core.data.remote.delete

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.xerahs.android.core.common.AwsV4Signer
import com.xerahs.android.core.common.S3Endpoint
import com.xerahs.android.core.data.remote.http.await
import com.xerahs.android.core.data.remote.transfer.FtpClientFactory
import com.xerahs.android.core.data.remote.transfer.SshSessionFactory
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Deletes an upload from its host. Throws on failure; 404 / missing file counts as success.
@Singleton
class RemoteDeleter @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sshSessionFactory: SshSessionFactory,
    private val ftpClientFactory: FtpClientFactory
) {

    fun canDelete(item: HistoryItem): Boolean = when (item.uploadDestination) {
        UploadDestination.CUSTOM_HTTP -> !item.deleteUrl.isNullOrBlank()
        UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP, UploadDestination.NEXTCLOUD ->
            !item.remoteKey.isNullOrBlank()
        else -> false
    }

    suspend fun delete(item: HistoryItem, config: UploadConfig) {
        require(canDelete(item)) { "Deleting from ${item.uploadDestination.displayName} isn't supported" }
        val key = item.remoteKey.orEmpty()
        when (config) {
            // Many deletion-page URLs are HTML confirmation pages that return 200 without
            // deleting anything (ShareX never auto-GETs these either); hand off to the user.
            is UploadConfig.CustomUploaderConfig -> throw OpenInBrowserException(item.deleteUrl!!)
            is UploadConfig.S3Config -> s3(config, key)
            is UploadConfig.FtpConfig -> withContext(Dispatchers.IO) { ftp(config, key) }
            is UploadConfig.SftpConfig -> withContext(Dispatchers.IO) { sftp(config, key) }
            is UploadConfig.NextcloudConfig -> nextcloud(config, key)
            else -> throw IllegalArgumentException("No delete support for this config")
        }
    }

    private suspend fun s3(config: UploadConfig.S3Config, key: String) {
        val target = S3Endpoint.resolve(config.endpoint, config.bucket, config.region, config.usePathStyle, key)
        val signed = AwsV4Signer.sign(
            method = "DELETE", url = target.url, headers = emptyMap(), payload = ByteArray(0),
            accessKeyId = config.accessKeyId, secretAccessKey = config.secretAccessKey,
            region = config.region, host = target.host
        )
        val request = Request.Builder().url(target.url).delete().apply {
            signed.headers.forEach { (k, v) -> header(k, v) }
            header("Authorization", signed.authorization)
        }.build()
        okHttpClient.newCall(request).await().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.peekBody(400).string()
                // A 404 for a missing object counts as success, but a 404 because the
                // bucket itself is gone/misconfigured is a real failure.
                if (resp.code == 404 && !body.contains("NoSuchBucket")) return@use
                throw IOException("S3 HTTP ${resp.code}: $body")
            }
        }
    }

    private fun ftp(config: UploadConfig.FtpConfig, path: String) {
        val client = ftpClientFactory.connect(
            config.host, config.port, config.username, config.password, config.useFtps, config.usePassiveMode
        )
        try {
            if (!client.deleteFile(path)) {
                // 550 covers both "no such file" and "permission denied"; tell them apart
                // by checking whether the file is actually still there.
                val stillExists = !client.listNames(path).isNullOrEmpty()
                if (stillExists) throw IOException("FTP delete failed: ${client.replyString?.trim()}")
            }
        } finally {
            runCatching { if (client.isConnected) { client.logout(); client.disconnect() } }
        }
    }

    private suspend fun sftp(config: UploadConfig.SftpConfig, path: String) {
        val session = sshSessionFactory.open(
            config.host, config.port, config.username, config.password,
            config.keyPath?.takeIf { it.isNotEmpty() }?.let { File(it).readBytes() },
            config.keyPassphrase, timeoutMs = 30000
        )
        var channel: ChannelSftp? = null
        try {
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(30000)
            try {
                channel.rm(path)
            } catch (e: SftpException) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw IOException("SFTP delete failed: ${e.message}", e)
            }
        } finally {
            channel?.disconnect()
            session.disconnect()
        }
    }

    private suspend fun nextcloud(config: UploadConfig.NextcloudConfig, key: String) {
        val url = config.serverUrl.trim().toHttpUrl().newBuilder()
            .addPathSegments("remote.php/dav/files").addPathSegment(config.username)
            .apply { key.split('/').filter { it.isNotBlank() }.forEach { addPathSegment(it) } }
            .build()
        val request = Request.Builder().url(url)
            .header("Authorization", Credentials.basic(config.username, config.appPassword)).delete().build()
        okHttpClient.newCall(request).await().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw IOException("Nextcloud HTTP ${resp.code}")
        }
    }
}
