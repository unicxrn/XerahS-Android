package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.data.remote.transfer.FtpClientFactory
import com.xerahs.android.core.data.remote.transfer.HostKeyChangedException
import com.xerahs.android.core.data.remote.transfer.HostKeyStore
import com.xerahs.android.core.data.remote.transfer.SshSessionFactory
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FtpConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sshSessionFactory: SshSessionFactory,
    private val ftpClientFactory: FtpClientFactory,
    private val hostKeyStore: HostKeyStore,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    /** 1 when opened with ?tab=sftp, otherwise 0. */
    val initialTab: Int = if (savedStateHandle.get<String>("tab") == "sftp") 1 else 0

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
        keyPath: String?, keyPassphrase: String?, remotePath: String, httpUrl: String
    ) {
        settingsRepository.saveSftpConfig(
            UploadConfig.SftpConfig(
                host = host, port = port, username = username, password = password,
                keyPath = keyPath, keyPassphrase = keyPassphrase,
                remotePath = remotePath, httpUrl = httpUrl
            )
        )
    }

    suspend fun testFtpConnection(
        host: String, port: Int, username: String, password: String, useFtps: Boolean, passive: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val client = ftpClientFactory.connect(host, port, username, password, useFtps, passive)
            try {
                "Connection successful (pwd: ${client.printWorkingDirectory()})"
            } finally {
                runCatching { client.logout(); client.disconnect() }
            }
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }

    suspend fun testSftpConnection(
        host: String, port: Int, username: String, password: String, keyPath: String?, keyPassphrase: String?
    ): String = withContext(Dispatchers.IO) {
        try {
            val key = keyPath?.takeIf { it.isNotEmpty() }?.let { File(it).readBytes() }
            val session = sshSessionFactory.open(host, port, username, password, key, keyPassphrase, timeoutMs = 10_000)
            session.disconnect()
            "Connection successful"
        } catch (e: HostKeyChangedException) {
            e.message.orEmpty()
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }

    /** Drops the pinned host key so the next connect trusts whatever key the server presents. */
    suspend fun forgetSftpHostKey(host: String, port: Int): Boolean {
        val hostPort = runCatching { SshSessionFactory.hostPort(host, port) }.getOrNull() ?: return false
        val had = hostKeyStore.get(hostPort) != null
        hostKeyStore.remove(hostPort)
        return had
    }
}
