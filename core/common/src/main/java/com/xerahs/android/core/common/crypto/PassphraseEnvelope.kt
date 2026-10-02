package com.xerahs.android.core.common.crypto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EnvelopeException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_JSON, WRONG_FORMAT, UNSUPPORTED, BAD_METADATA, WRONG_PASSPHRASE }
}

// XerahS passphrase envelope (.xsdc / .xsbk): PBKDF2-HMAC-SHA256 key, AES-256-GCM, 16-byte tag stored separately.
object PassphraseEnvelope {
    const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val TAG_BYTES = 16

    fun formatOf(text: String): String? = runCatching {
        JsonParser.parseString(text.trim().removePrefix("﻿")).asJsonObject.get("Format")?.asString
    }.getOrNull()

    fun seal(format: String, plain: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations)
        val sealed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
                doFinal(plain)
            }
        } finally {
            key.fill(0)
        }
        val b64 = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("Format", format)
            addProperty("FormatVersion", 1)
            add("Encryption", JsonObject().apply {
                addProperty("Method", "Passphrase")
                addProperty("Kdf", "PBKDF2-HMAC-SHA256")
                addProperty("Iterations", iterations)
                addProperty("Salt", b64.encodeToString(salt))
                addProperty("Cipher", "AES-256-GCM")
                addProperty("Nonce", b64.encodeToString(nonce))
                addProperty("Tag", b64.encodeToString(sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size)))
            })
            addProperty("Payload", b64.encodeToString(sealed.copyOfRange(0, sealed.size - TAG_BYTES)))
        }.toString()
    }

    fun open(text: String, format: String, passphrase: CharArray): ByteArray {
        val envelope = try {
            JsonParser.parseString(text.trim().removePrefix("﻿")).asJsonObject
        } catch (e: Exception) {
            throw EnvelopeException(EnvelopeException.Reason.NOT_JSON, e)
        }
        if (envelope.str("Format") != format || envelope.int("FormatVersion") != 1) {
            throw EnvelopeException(EnvelopeException.Reason.WRONG_FORMAT)
        }
        val enc = envelope.get("Encryption")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw EnvelopeException(EnvelopeException.Reason.BAD_METADATA)
        if (enc.str("Method") != "Passphrase" || enc.str("Kdf") != "PBKDF2-HMAC-SHA256" || enc.str("Cipher") != "AES-256-GCM") {
            throw EnvelopeException(EnvelopeException.Reason.UNSUPPORTED)
        }
        val iterations = enc.int("Iterations")?.takeIf { it in 1..MAX_ITERATIONS }
            ?: throw EnvelopeException(EnvelopeException.Reason.BAD_METADATA)
        return try {
            val b64 = Base64.getDecoder()
            val key = deriveKey(passphrase, b64.decode(enc.str("Salt").orEmpty()), iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                    GCMParameterSpec(TAG_BYTES * 8, b64.decode(enc.str("Nonce").orEmpty())))
            } finally {
                key.fill(0)
            }
            cipher.doFinal(b64.decode(envelope.str("Payload").orEmpty()) + b64.decode(enc.str("Tag").orEmpty()))
        } catch (e: Exception) {
            throw EnvelopeException(EnvelopeException.Reason.WRONG_PASSPHRASE, e)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
}
