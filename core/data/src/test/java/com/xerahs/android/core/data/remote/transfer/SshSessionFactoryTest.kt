package com.xerahs.android.core.data.remote.transfer

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.runBlocking
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
        factory.verify("example.com:22", fakeServer(key))
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
}
