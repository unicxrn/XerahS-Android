package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpUploader @Inject constructor() {

    suspend fun upload(file: File, config: UploadConfig.FtpConfig, remoteFileName: String? = null): UploadResult =
        withContext(Dispatchers.IO) {
            val client: FTPClient = if (config.useFtps) FTPSClient() else FTPClient()

            try {
                client.connect(config.host, config.port)

                if (!client.login(config.username, config.password)) {
                    return@withContext UploadResult(
                        success = false,
                        errorMessage = "FTP login failed",
                        destination = UploadDestination.FTP
                    )
                }

                if (config.usePassiveMode) {
                    client.enterLocalPassiveMode()
                } else {
                    client.enterLocalActiveMode()
                }

                client.setFileType(FTP.BINARY_FILE_TYPE)

                // Change to remote directory
                val remotePath = config.remotePath.trimEnd('/')
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

                    UploadResult(
                        success = true,
                        url = url,
                        destination = UploadDestination.FTP
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
                    if (client.isConnected) {
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
