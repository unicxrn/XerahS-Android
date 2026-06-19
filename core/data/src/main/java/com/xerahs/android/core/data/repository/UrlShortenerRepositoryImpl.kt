package com.xerahs.android.core.data.repository

import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import kotlinx.coroutines.Dispatchers
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
) : UrlShortenerRepository {
    override suspend fun shorten(longUrl: String): Result<String> = withContext(Dispatchers.IO) {
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
