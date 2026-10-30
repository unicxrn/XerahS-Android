package com.xerahs.android.core.data.remote.transfer

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens SSH sessions with trust-on-first-use host key checking. The first connect to a
 * `host:port` pins the server key's fingerprint; later connects must present the same key
 * or fail with [HostKeyChangedException].
 */
@Singleton
class SshSessionFactory @Inject constructor(private val store: HostKeyStore) {

    suspend fun open(
        host: String,
        port: Int,
        user: String,
        password: String?,
        privateKey: ByteArray?,
        passphrase: String?,
        timeoutMs: Int = 15000
    ): Session = withContext(Dispatchers.IO) {
        verify(hostPort(host, port), onRejected = { it.disconnect() }) { repo ->
            val jsch = JSch()
            jsch.setHostKeyRepository(repo)
            if (privateKey != null) {
                jsch.addIdentity("xerahs-key", privateKey, null, passphrase?.takeIf { it.isNotEmpty() }?.toByteArray())
            }
            val session = jsch.getSession(user, host, port)
            if (privateKey == null && password != null) session.setPassword(password)
            session.setConfig("StrictHostKeyChecking", "yes")
            session.timeout = timeoutMs
            try {
                session.connect(timeoutMs)
            } catch (e: Exception) {
                session.disconnect()
                throw e
            }
            session
        }
    }

    /**
     * Runs [connect] with a repository that checks the presented key against the pinned
     * one, then pins on success (first use) or turns a key mismatch into
     * [HostKeyChangedException]. Split out so the policy is testable without a server.
     */
    internal suspend fun <T> verify(
        hostPort: String,
        onRejected: (T) -> Unit = {},
        connect: (HostKeyRepository) -> T
    ): T {
        val pinned = store.get(hostPort)
        val repo = PinningRepository(pinned)
        val result = try {
            connect(repo)
        } catch (e: Exception) {
            val actual = repo.presented
            if (pinned != null && actual != null && actual != pinned) {
                throw HostKeyChangedException(hostPort, pinned, actual)
            }
            throw e
        }
        val presented = repo.presented
        if (pinned == null && presented != null) {
            // Another connect may have pinned a key since we read the store; whoever pins first wins.
            val existing = store.putIfAbsent(hostPort, presented)
            if (existing != null && existing != presented) {
                runCatching { onRejected(result) }
                throw HostKeyChangedException(hostPort, existing, presented)
            }
        }
        return result
    }

    private class PinningRepository(private val pinned: String?) : HostKeyRepository {
        @Volatile var presented: String? = null

        override fun check(host: String?, key: ByteArray): Int {
            val fp = fingerprint(key)
            presented = fp
            return when (pinned) {
                null, fp -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "xerahs-pinned"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    companion object {
        fun hostPort(host: String, port: Int): String {
            require(host.none { it == '=' || it == '\n' || it == '\r' }) {
                "Invalid host name: it can't contain '=' or line breaks"
            }
            return "${host.trim().lowercase()}:$port"
        }

        /** OpenSSH-style: `SHA256:` + unpadded base64 of sha256(key blob). */
        fun fingerprint(keyBlob: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(keyBlob))
    }
}
