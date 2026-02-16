package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
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

    suspend fun loadConfig(): UploadConfig.CustomHttpConfig =
        settingsRepository.getCustomHttpConfig()

    suspend fun saveConfig(
        url: String, method: String, headers: Map<String, String>,
        responseUrlJsonPath: String, formFieldName: String
    ) {
        settingsRepository.saveCustomHttpConfig(
            UploadConfig.CustomHttpConfig(
                url = url,
                method = method,
                headers = headers,
                responseUrlJsonPath = responseUrlJsonPath,
                formFieldName = formFieldName
            )
        )
    }

    suspend fun testConnection(url: String, method: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
                requestBuilder.head()

                val response = client.newCall(requestBuilder.build()).execute()
                when {
                    response.code in 200..499 -> "Endpoint reachable (HTTP ${response.code})"
                    else -> "Endpoint returned HTTP ${response.code}"
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
