package com.xerahs.android.core.data.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.common.crypto.EnvelopeException
import com.xerahs.android.core.common.crypto.PassphraseEnvelope

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

    fun decode(bytes: ByteArray, passphrase: CharArray): List<XsdcDestination> {
        val plain = try {
            PassphraseEnvelope.open(String(bytes, Charsets.UTF_8), "XerahS.DestinationConfig", passphrase)
        } catch (e: EnvelopeException) {
            throw XsdcException(
                when (e.reason) {
                    EnvelopeException.Reason.NOT_JSON -> "The .xsdc file is not valid JSON."
                    EnvelopeException.Reason.WRONG_FORMAT -> "This is not a XerahS destination config."
                    EnvelopeException.Reason.UNSUPPORTED -> "This .xsdc encryption method is not supported."
                    EnvelopeException.Reason.BAD_METADATA -> "This .xsdc file has invalid encryption metadata."
                    EnvelopeException.Reason.WRONG_PASSPHRASE -> "Wrong passphrase or damaged file."
                },
                e
            )
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
