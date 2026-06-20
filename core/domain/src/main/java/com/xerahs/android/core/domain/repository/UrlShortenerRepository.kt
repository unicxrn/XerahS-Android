package com.xerahs.android.core.domain.repository

interface UrlShortenerRepository {
    /** Returns the shortened URL, or Result.failure on error. */
    suspend fun shorten(longUrl: String): Result<String>
}
