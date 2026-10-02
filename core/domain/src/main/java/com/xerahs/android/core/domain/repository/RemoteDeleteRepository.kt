package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.HistoryItem

// The host wants the user to open this page to finish deleting.
class OpenInBrowserException(val url: String) : Exception("Open the deletion page to finish")

interface RemoteDeleteRepository {
    fun canDelete(item: HistoryItem): Boolean
    suspend fun delete(item: HistoryItem): Result<Unit>
}
