package com.xerahs.android.core.common.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PassphraseEnvelopeTest {
    private val pass = "correct horse".toCharArray()

    @Test fun roundTrips() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        assertEquals("Test.Format", PassphraseEnvelope.formatOf(sealed))
        assertArrayEquals("hello".toByteArray(), PassphraseEnvelope.open(sealed, "Test.Format", pass.copyOf()))
    }

    @Test fun wrongPassphrase() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        val e = runCatching { PassphraseEnvelope.open(sealed, "Test.Format", "nope".toCharArray()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.WRONG_PASSPHRASE, (e as EnvelopeException).reason)
    }

    @Test fun wrongFormat() {
        val sealed = PassphraseEnvelope.seal("Test.Format", "hello".toByteArray(), pass.copyOf(), iterations = 1000)
        val e = runCatching { PassphraseEnvelope.open(sealed, "Other", pass.copyOf()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.WRONG_FORMAT, (e as EnvelopeException).reason)
    }

    @Test fun notJson() {
        assertEquals(null, PassphraseEnvelope.formatOf("plain text"))
        val e = runCatching { PassphraseEnvelope.open("plain text", "Test.Format", pass.copyOf()) }.exceptionOrNull()
        assertEquals(EnvelopeException.Reason.NOT_JSON, (e as EnvelopeException).reason)
    }
}
