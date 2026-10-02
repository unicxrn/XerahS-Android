package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.common.sxcu.SxcuWriter
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class CustomHttpConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadSxcu(): String = SxcuWriter.write(settingsRepository.getCustomUploaderConfig().spec)

    /** Saves [sxcu] if valid. Returns null on success, otherwise the error message. */
    suspend fun save(sxcu: String): String? = SxcuParser.parse(sxcu).fold(
        onSuccess = { settingsRepository.saveCustomUploaderConfig(UploadConfig.CustomUploaderConfig(it)); null },
        onFailure = { it.message ?: "Not a valid custom uploader" }
    )

    /** Validates [sxcu] and returns it re-formatted, or null if invalid. */
    fun normalize(sxcu: String): String? = SxcuParser.parse(sxcu).getOrNull()?.let(SxcuWriter::write)

    suspend fun testConnection(sxcu: String): String = withContext(Dispatchers.IO) {
        val spec = SxcuParser.parse(sxcu).getOrElse { return@withContext "Fix the definition first: ${it.message}" }
        val url = runCatching { ShareXSyntax.evaluate(spec.requestURL, SyntaxContext(fileName = "test.png")) }
            .getOrElse { return@withContext "Request URL can't be evaluated: ${it.message}" }
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                if (response.code in 200..499) "Endpoint reachable (HTTP ${response.code})"
                else "Endpoint returned HTTP ${response.code}"
            }
        } catch (e: java.net.UnknownHostException) {
            "Connection failed: could not resolve host."
        } catch (e: java.net.SocketTimeoutException) {
            "Connection timed out."
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}
