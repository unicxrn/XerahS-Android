package com.xerahs.android.core.data.remote.transfer

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SshSessionFactoryTest {
    private class MemoryStore : HostKeyStore {
        val map = mutableMapOf<String, String>()
        override suspend fun get(hostPort: String) = map[hostPort]
        override suspend fun put(hostPort: String, fingerprint: String) { map[hostPort] = fingerprint }
        override suspend fun remove(hostPort: String) { map.remove(hostPort) }
        override suspend fun all(): Map<String, String> = map.toMap()
        override suspend fun putIfAbsent(hostPort: String, fingerprint: String): String? =
            synchronized(map) { map[hostPort] ?: run { map[hostPort] = fingerprint; null } }
    }

    private val store = MemoryStore()
    private val factory = SshSessionFactory(store)
    private val keyA = byteArrayOf(0, 0, 0, 7) + "ssh-rsa".toByteArray() + ByteArray(32) { 1 }
    private val keyB = byteArrayOf(0, 0, 0, 7) + "ssh-rsa".toByteArray() + ByteArray(32) { 2 }

    // Stands in for JSch: presents the server key to the repository, and fails the
    // handshake the way JSch does with StrictHostKeyChecking=yes when it isn't OK.
    private fun fakeServer(key: ByteArray): (HostKeyRepository) -> String = { repo ->
        val verdict = repo.check("example.com", key)
        if (verdict != HostKeyRepository.OK) throw JSchException("HostKey has been changed")
        "session"
    }

    private fun connect(key: ByteArray) = runBlocking {
        factory.verify("example.com:22", connect = fakeServer(key))
    }

    @Test fun firstConnectPinsFingerprint() {
        assertNull(store.map["example.com:22"])
        assertEquals("session", connect(keyA))
        assertEquals(SshSessionFactory.fingerprint(keyA), store.map["example.com:22"])
        assertTrue(store.map["example.com:22"]!!.startsWith("SHA256:"))
        assertTrue(!store.map["example.com:22"]!!.endsWith("="))
    }

    @Test fun matchingFingerprintConnects() {
        store.map["example.com:22"] = SshSessionFactory.fingerprint(keyA)
        assertEquals("session", connect(keyA))
        assertEquals(SshSessionFactory.fingerprint(keyA), store.map["example.com:22"])
    }

    @Test fun mismatchThrowsHostKeyChanged() {
        store.map["example.com:22"] = SshSessionFactory.fingerprint(keyA)
        try {
            connect(keyB)
            fail("expected HostKeyChangedException")
        } catch (e: HostKeyChangedException) {
            assertEquals("example.com:22", e.hostPort)
            assertEquals(SshSessionFactory.fingerprint(keyA), e.expected)
            assertEquals(SshSessionFactory.fingerprint(keyB), e.actual)
            assertTrue(e.message!!.startsWith("The server's identity changed."))
        }
        // The pinned key is left alone.
        assertEquals(SshSessionFactory.fingerprint(keyA), store.map["example.com:22"])
    }

    @Test fun forgetThenReconnectPinsNewKey() {
        store.map["example.com:22"] = SshSessionFactory.fingerprint(keyA)
        runCatching { connect(keyB) }
        runBlocking { store.remove("example.com:22") }
        assertEquals("session", connect(keyB))
        assertEquals(SshSessionFactory.fingerprint(keyB), store.map["example.com:22"])
    }

    @Test fun fingerprintIsOpenSshStyle() {
        // sha256("") base64 without padding
        assertEquals("SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU", SshSessionFactory.fingerprint(ByteArray(0)))
    }

    @Test fun otherFailuresPassThrough() {
        try {
            runBlocking { factory.verify("example.com:22") { throw JSchException("Auth fail") } }
            fail()
        } catch (e: JSchException) {
            assertEquals("Auth fail", e.message)
        }
        assertNull(store.map["example.com:22"])
    }

    @Test fun concurrentFirstConnectsPinOnlyOneKey() = runBlocking {
        // Both connects read "nothing pinned" before either finishes the handshake.
        val bothChecked = CountDownLatch(2)
        fun racing(key: ByteArray): (HostKeyRepository) -> String = { repo ->
            val result = fakeServer(key)(repo)
            bothChecked.countDown()
            check(bothChecked.await(5, TimeUnit.SECONDS))
            result
        }
        val a = async(Dispatchers.Default) { runCatching { factory.verify("example.com:22", connect = racing(keyA)) } }
        val b = async(Dispatchers.Default) { runCatching { factory.verify("example.com:22", connect = racing(keyB)) } }
        val results = listOf(a.await(), b.await())

        assertEquals(1, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertTrue(failure is HostKeyChangedException)
        val pinned = store.map["example.com:22"]
        assertEquals(pinned, (failure as HostKeyChangedException).expected)
        assertTrue(pinned == SshSessionFactory.fingerprint(keyA) || pinned == SshSessionFactory.fingerprint(keyB))
    }

    @Test fun hostPortRejectsSeparatorCharacters() {
        assertEquals("example.com:22", SshSessionFactory.hostPort(" Example.COM ", 22))
        for (bad in listOf("a=b", "a\nb", "a\rb")) {
            try {
                SshSessionFactory.hostPort(bad, 22)
                fail("expected rejection of ${bad}")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("host"))
            }
        }
    }
}
