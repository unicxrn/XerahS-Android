package com.xerahs.android.core.data.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class XsdcException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class XsdcDestination(
    val providerId: String,
    val displayName: String,
    val isDefault: Boolean,
    val config: JsonObject,
)

/**
 * Decrypts a XerahS destination config (.xsdc): JSON envelope, PBKDF2-HMAC-SHA256 key,
 * AES-256-GCM payload with the 16-byte tag stored separately. Implemented from the format;
 * the passphrase is never stored.
 */
object XsdcDecoder {
    private const val MAX_ITERATIONS = 10_000_000

    fun decode(bytes: ByteArray, passphrase: CharArray): List<XsdcDestination> {
        val envelope = try {
            JsonParser.parseString(String(bytes, Charsets.UTF_8).trim().removePrefix("﻿")).asJsonObject
        } catch (e: Exception) {
            throw XsdcException("The .xsdc file is not valid JSON.", e)
        }
        if (envelope.str("Format") != "XerahS.DestinationConfig" || envelope.int("FormatVersion") != 1) {
            throw XsdcException("This is not a XerahS destination config.")
        }
        val enc = envelope.get("Encryption")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw XsdcException("The .xsdc file is missing encryption metadata.")
        if (enc.str("Method") != "Passphrase" || enc.str("Kdf") != "PBKDF2-HMAC-SHA256" ||
            enc.str("Cipher") != "AES-256-GCM"
        ) throw XsdcException("This .xsdc encryption method is not supported.")
        val iterations = enc.int("Iterations")?.takeIf { it in 1..MAX_ITERATIONS }
            ?: throw XsdcException("This .xsdc file has invalid encryption metadata.")

        val plain = try {
            val b64 = Base64.getDecoder()
            val spec = PBEKeySpec(passphrase, b64.decode(enc.str("Salt").orEmpty()), iterations, 256)
            val key = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, b64.decode(enc.str("Nonce").orEmpty())))
            } finally {
                key.fill(0)
            }
            cipher.doFinal(b64.decode(envelope.str("Payload").orEmpty()) + b64.decode(enc.str("Tag").orEmpty()))
        } catch (e: Exception) {
            throw XsdcException("Wrong passphrase or damaged file.", e)
        }

        val payload = try {
            JsonParser.parseString(String(plain, Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            throw XsdcException("The decrypted .xsdc payload is invalid.", e)
        }
        return payload.get("Destinations")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject }
            ?.map { d ->
                XsdcDestination(
                    providerId = d.str("ProviderId").orEmpty(),
                    displayName = d.str("DisplayName").orEmpty(),
                    isDefault = d.get("IsDefault")?.takeIf { it.isJsonPrimitive }
                        ?.let { runCatching { it.asBoolean }.getOrNull() } ?: false,
                    config = d.get("Config")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject(),
                )
            }.orEmpty()
    }

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive }
        ?.let { runCatching { it.asInt }.getOrNull() }
}
