package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.remote.custom.CustomUploadInput
import com.xerahs.android.core.data.remote.custom.CustomUploadOutcome
import com.xerahs.android.core.data.remote.custom.CustomUploaderClient
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject

/** Top-level so it is unit-testable without Android. */
fun buildIsGdRequestUrl(longUrl: String): String =
    "https://is.gd/create.php?format=simple&url=" + URLEncoder.encode(longUrl, "UTF-8")

class UrlShortenerRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
    private val customUploaderClient: CustomUploaderClient,
) : UrlShortenerRepository {
    override suspend fun shorten(longUrl: String): Result<String> {
        val config = withContext(Dispatchers.IO) {
            val profileId = settingsRepository.getShortenerProfileId().first()
            profileId?.let {
                profileRepository.getProfileConfig(it, UploadDestination.CUSTOM_HTTP) as? UploadConfig.CustomUploaderConfig
            }
        }
        return if (config != null && config.spec.requestURL.isNotBlank()) shortenCustom(config, longUrl)
        else shortenIsGd(longUrl)
    }

    private suspend fun shortenCustom(config: UploadConfig.CustomUploaderConfig, longUrl: String): Result<String> =
        when (val outcome = customUploaderClient.execute(config.spec, CustomUploadInput(null, "", input = longUrl))) {
            is CustomUploadOutcome.Success -> Result.success(outcome.url)
            is CustomUploadOutcome.Failure -> Result.failure(IllegalStateException(outcome.message))
        }

    private suspend fun shortenIsGd(longUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(buildIsGdRequestUrl(longUrl)).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                if (resp.isSuccessful && body.startsWith("http")) Result.success(body)
                else Result.failure(IllegalStateException("Shorten failed: ${resp.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
