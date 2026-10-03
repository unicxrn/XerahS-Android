package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.AfterUploadAction

// Returns the short link when SHORTEN_URL is selected and shortening works, else the original URL.
internal suspend fun maybeShorten(
    url: String?,
    actions: Set<AfterUploadAction>,
    shorten: suspend (String) -> Result<String>,
): String? {
    if (url == null || AfterUploadAction.SHORTEN_URL !in actions || !url.startsWith("http", ignoreCase = true)) return url
    return shorten(url).getOrNull()?.takeIf { it.isNotBlank() } ?: url
}
