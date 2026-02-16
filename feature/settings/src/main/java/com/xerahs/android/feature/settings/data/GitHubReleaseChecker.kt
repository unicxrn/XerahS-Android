package com.xerahs.android.feature.settings.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubReleaseChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    suspend fun fetchLatestRelease(): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        runCatching {
            // Use /releases (not /releases/latest) so pre-releases are included
            val request = Request.Builder()
                .url("$BASE_URL/releases?per_page=1")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("GitHub API error: ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response body")
            val type = object : TypeToken<List<GitHubRelease>>() {}.type
            val releases = gson.fromJson<List<GitHubRelease>>(body, type)
            releases.firstOrNull() ?: throw Exception("No releases found")
        }
    }

    suspend fun fetchAllReleases(): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/releases")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("GitHub API error: ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response body")
            val type = object : TypeToken<List<GitHubRelease>>() {}.type
            gson.fromJson<List<GitHubRelease>>(body, type)
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    companion object {
        private const val BASE_URL = "https://api.github.com/repos/unicxrn/XerahS-Android"
    }
}

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long
)
