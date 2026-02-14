package com.xerahs.android.feature.upload.uploader

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
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
class SftpUploader @Inject constructor() {

    suspend fun upload(file: File, config: UploadConfig.SftpConfig, remoteFileName: String? = null): UploadResult =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelSftp? = null

            try {
                val jsch = JSch()

                // Add SSH key if provided
                if (!config.keyPath.isNullOrEmpty()) {
                    if (config.keyPassphrase.isNullOrEmpty()) {
                        jsch.addIdentity(config.keyPath)
                    } else {
                        jsch.addIdentity(config.keyPath, config.keyPassphrase)
                    }
                }

                session = jsch.getSession(config.username, config.host, config.port)

                if (config.keyPath.isNullOrEmpty()) {
                    session.setPassword(config.password)
                }

                // Disable strict host key checking for simplicity
                val sshConfig = java.util.Properties()
                sshConfig["StrictHostKeyChecking"] = "no"
                session.setConfig(sshConfig)

                session.connect(30000)

                channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(30000)

                // Navigate to remote directory
                val remotePath = config.remotePath.trimEnd('/')
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

                val url = if (config.httpUrl.isNotEmpty()) {
                    "${config.httpUrl.trimEnd('/')}/$uploadName"
                } else {
                    "sftp://${config.host}$remotePath/$uploadName"
                }

                UploadResult(
                    success = true,
                    url = url,
                    destination = UploadDestination.SFTP
                )
            } catch (e: Exception) {
                UploadResult(
                    success = false,
                    errorMessage = "SFTP error: ${e.message}",
                    destination = UploadDestination.SFTP
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
