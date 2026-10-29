package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.common.PathPattern
import com.xerahs.android.core.data.remote.transfer.FtpClientFactory
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpUploader @Inject constructor(private val ftpClientFactory: FtpClientFactory) {

    suspend fun upload(file: File, config: UploadConfig.FtpConfig, remoteFileName: String? = null): UploadResult =
        withContext(Dispatchers.IO) {
            var client: FTPClient? = null

            try {
                client = ftpClientFactory.connect(
                    config.host, config.port, config.username, config.password,
                    config.useFtps, config.usePassiveMode
                )

                // Change to remote directory
                val remotePath = PathPattern.resolve(config.remotePath).trimEnd('/')
                if (remotePath.isNotEmpty() && remotePath != "/") {
                    if (!client.changeWorkingDirectory(remotePath)) {
                        // Try to create directories
                        createDirectories(client, remotePath)
                        client.changeWorkingDirectory(remotePath)
                    }
                }

                // Upload
                @Suppress("NAME_SHADOWING")
                val remoteFileName = remoteFileName ?: file.name
                val success = FileInputStream(file).use { inputStream ->
                    client.storeFile(remoteFileName, inputStream)
                }

                if (success) {
                    val url = if (config.httpUrl.isNotEmpty()) {
                        "${config.httpUrl.trimEnd('/')}/$remoteFileName"
                    } else {
                        "ftp://${config.host}$remotePath/$remoteFileName"
                    }

                    // The actual directory the file landed in: when remotePath was blank
                    // and no cd happened, this is the login home dir, not "/". If the
                    // server won't tell us, don't guess a path -- leave remoteKey null
                    // so delete isn't offered for a path we aren't sure about.
                    val actualDir = client.printWorkingDirectory()
                    UploadResult(
                        success = true,
                        url = url,
                        destination = UploadDestination.FTP,
                        remoteKey = actualDir?.let { it.trimEnd('/') + "/" + remoteFileName }
                    )
                } else {
                    UploadResult(
                        success = false,
                        errorMessage = "FTP upload failed: ${client.replyString}",
                        destination = UploadDestination.FTP
                    )
                }
            } catch (e: Exception) {
                UploadResult(
                    success = false,
                    errorMessage = "FTP error: ${e.message}",
                    destination = UploadDestination.FTP
                )
            } finally {
                try {
                    if (client?.isConnected == true) {
                        client.logout()
                        client.disconnect()
                    }
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }

    private fun createDirectories(client: FTPClient, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = "$current/$part"
            client.makeDirectory(current)
        }
    }
}
