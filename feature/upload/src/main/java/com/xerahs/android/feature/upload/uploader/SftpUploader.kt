package com.xerahs.android.feature.upload.uploader

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.xerahs.android.core.common.PathPattern
import com.xerahs.android.core.data.remote.transfer.HostKeyChangedException
import com.xerahs.android.core.data.remote.transfer.SshSessionFactory
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpUploader @Inject constructor(private val sshSessionFactory: SshSessionFactory) {

    suspend fun upload(file: File, config: UploadConfig.SftpConfig, remoteFileName: String? = null): UploadResult =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelSftp? = null

            try {
                session = sshSessionFactory.open(
                    config.host, config.port, config.username, config.password,
                    config.keyPath?.takeIf { it.isNotEmpty() }?.let { File(it).readBytes() },
                    config.keyPassphrase, timeoutMs = 30000
                )

                channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(30000)

                // Navigate to remote directory. Resolved once so the link uses the same date folders.
                val remotePath = PathPattern.resolve(config.remotePath).trimEnd('/')
                if (remotePath.isNotEmpty() && remotePath != "/") {
                    try {
                        channel.cd(remotePath)
                    } catch (e: Exception) {
                        // Try to create directories
                        createDirectories(channel, remotePath)
                        channel.cd(remotePath)
                    }
                }

                // Upload file
                val uploadName = remoteFileName ?: file.name
                FileInputStream(file).use { inputStream ->
                    channel.put(inputStream, uploadName)
                }

                val url = RemoteLink.build(
                    config.httpUrl, config.remotePath, remotePath, uploadName,
                    fallbackBase = "sftp://${config.host}"
                )

                // The actual directory the file landed in: when remotePath was blank
                // and no cd happened, this is the login home dir, not "/". If we can't
                // determine it, don't guess a path -- leave remoteKey null so delete
                // isn't offered for a path we aren't sure about.
                val actualDir = try {
                    channel.pwd()
                } catch (e: Exception) {
                    null
                }
                UploadResult(
                    success = true,
                    url = url,
                    destination = UploadDestination.SFTP,
                    remoteKey = actualDir?.let { it.trimEnd('/') + "/" + uploadName }
                )
            } catch (e: HostKeyChangedException) {
                UploadResult(
                    success = false,
                    errorMessage = e.message,
                    destination = UploadDestination.SFTP,
                    error = e
                )
            } catch (e: Exception) {
                UploadResult(
                    success = false,
                    errorMessage = "SFTP error: ${e.message}",
                    destination = UploadDestination.SFTP,
                    error = e
                )
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }

    private fun createDirectories(channel: ChannelSftp, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = "$current/$part"
            try {
                channel.stat(current)
            } catch (e: Exception) {
                channel.mkdir(current)
            }
        }
    }
}
