package com.xerahs.android.core.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class XsdcDecoderTest {
    companion object {
        /** Builds an .xsdc envelope exactly like upstream DestinationConfigExportService. */
        fun encrypt(payload: String, passphrase: String, iterations: Int = 1000): ByteArray {
            val rnd = SecureRandom()
            val salt = ByteArray(16).also(rnd::nextBytes)
            val nonce = ByteArray(12).also(rnd::nextBytes)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val out = cipher.doFinal(payload.toByteArray())
            val cipherText = out.copyOfRange(0, out.size - 16)
            val tag = out.copyOfRange(out.size - 16, out.size)
            val b64 = Base64.getEncoder()
            val envelope = JsonObject().apply {
                addProperty("Format", "XerahS.DestinationConfig")
                addProperty("FormatVersion", 1)
                add("Encryption", JsonObject().apply {
                    addProperty("Method", "Passphrase"); addProperty("Kdf", "PBKDF2-HMAC-SHA256")
                    addProperty("Iterations", iterations); addProperty("Salt", b64.encodeToString(salt))
                    addProperty("Cipher", "AES-256-GCM"); addProperty("Nonce", b64.encodeToString(nonce))
                    addProperty("Tag", b64.encodeToString(tag))
                })
                addProperty("Payload", b64.encodeToString(cipherText))
            }
            return envelope.toString().toByteArray()
        }

        fun s3Payload(): String = JsonObject().apply {
            addProperty("Format", "XerahS.DestinationConfig.Payload")
            addProperty("FormatVersion", 1)
            add("Destinations", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ProviderId", "amazons3"); addProperty("DisplayName", "Desktop S3")
                    addProperty("IsDefault", true)
                    add("Config", JsonObject().apply {
                        addProperty("AuthMode", "AccessKeys"); addProperty("AccessKeyId", "AK")
                        addProperty("SecretAccessKey", "SK"); addProperty("BucketName", "b1")
                        addProperty("Region", "eu-west-1"); addProperty("Endpoint", "")
                        addProperty("UsePathStyle", false); addProperty("UseCustomDomain", true)
                        addProperty("CustomDomain", "https://cdn.test"); addProperty("SetPublicAcl", true)
                    })
                })
                add(JsonObject().apply {
                    addProperty("ProviderId", "dropbox"); addProperty("DisplayName", "DB")
                    add("Config", JsonObject())
                })
            })
        }.toString()
    }

    @Test fun decryptsDestinations() {
        val dests = XsdcDecoder.decode(encrypt(s3Payload(), "correct horse"), "correct horse".toCharArray())
        assertEquals(2, dests.size)
        assertEquals("amazons3", dests[0].providerId)
        assertEquals("Desktop S3", dests[0].displayName)
        assertTrue(dests[0].isDefault)
        assertEquals("b1", dests[0].config.get("BucketName").asString)
    }

    @Test fun wrongPassphraseIsReported() {
        val e = runCatching { XsdcDecoder.decode(encrypt(s3Payload(), "right"), "wrong".toCharArray()) }.exceptionOrNull()
        assertTrue(e is XsdcException)
        assertEquals("Wrong passphrase or damaged file.", e!!.message)
    }

    @Test fun rejectsNonXsdcJson() {
        val e = runCatching { XsdcDecoder.decode("""{"Format":"Other"}""".toByteArray(), "x".toCharArray()) }.exceptionOrNull()
        assertTrue(e is XsdcException)
    }

    @Test fun rejectsExcessiveIterationsWithoutAttemptingDecrypt() {
        // Build a real envelope cheaply (1000 iterations), then tamper the declared
        // Iterations count so a naive implementation would run PBKDF2 2 billion times.
        val envelope = JsonParser.parseString(String(encrypt(s3Payload(), "p", iterations = 1000))).asJsonObject
        envelope.getAsJsonObject("Encryption").addProperty("Iterations", 2_000_000_000)
        val tampered = envelope.toString().toByteArray()

        val start = System.nanoTime()
        val e = runCatching { XsdcDecoder.decode(tampered, "p".toCharArray()) }.exceptionOrNull()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(e is XsdcException)
        assertEquals("This .xsdc file has invalid encryption metadata.", e!!.message)
        assertTrue("decode should reject oversized Iterations quickly, took ${elapsedMs}ms", elapsedMs < 2000)
    }
}
