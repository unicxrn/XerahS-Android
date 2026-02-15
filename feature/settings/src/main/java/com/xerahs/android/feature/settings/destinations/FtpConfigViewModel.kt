package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import javax.inject.Inject

@HiltViewModel
class FtpConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadFtpConfig(): UploadConfig.FtpConfig =
        settingsRepository.getFtpConfig()

    suspend fun loadSftpConfig(): UploadConfig.SftpConfig =
        settingsRepository.getSftpConfig()

    suspend fun saveFtpConfig(
        host: String, port: Int, username: String, password: String,
        remotePath: String, useFtps: Boolean, usePassiveMode: Boolean, httpUrl: String
    ) {
        settingsRepository.saveFtpConfig(
            UploadConfig.FtpConfig(
                host = host, port = port, username = username, password = password,
                remotePath = remotePath, useFtps = useFtps, usePassiveMode = usePassiveMode,
                httpUrl = httpUrl
            )
        )
    }

    suspend fun saveSftpConfig(
        host: String, port: Int, username: String, password: String,
        keyPath: String?, remotePath: String, httpUrl: String
    ) {
        settingsRepository.saveSftpConfig(
            UploadConfig.SftpConfig(
                host = host, port = port, username = username, password = password,
                keyPath = keyPath, remotePath = remotePath, httpUrl = httpUrl
            )
        )
    }

    suspend fun testFtpConnection(
        host: String, port: Int, username: String, password: String, useFtps: Boolean
    ): String = withContext(Dispatchers.IO) {
        val client: FTPClient = if (useFtps) FTPSClient() else FTPClient()
        try {
            client.connectTimeout = 10_000
            client.connect(host, port)
            if (!client.login(username, password)) {
                return@withContext "Login failed: ${client.replyString?.trim()}"
            }
            val pwd = client.printWorkingDirectory()
            client.logout()
            client.disconnect()
            "Connection successful (pwd: $pwd)"
        } catch (e: Exception) {
            try { client.disconnect() } catch (_: Exception) {}
            "Connection failed: ${e.message}"
        }
    }

    suspend fun testSftpConnection(
        host: String, port: Int, username: String, password: String, keyPath: String?
    ): String = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            val jsch = JSch()
            if (!keyPath.isNullOrEmpty()) {
                jsch.addIdentity(keyPath)
            }
            session = jsch.getSession(username, host, port)
            if (keyPath.isNullOrEmpty()) {
                session.setPassword(password)
            }
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 10_000
            session.connect()
            session.disconnect()
            "Connection successful"
        } catch (e: Exception) {
            try { session?.disconnect() } catch (_: Exception) {}
            "Connection failed: ${e.message}"
        }
    }
}
