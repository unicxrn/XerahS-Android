package com.xerahs.android.feature.upload.worker

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Records a finished upload. Runs to the end even if the work is cancelled meanwhile: the file is
 * already on the server, so losing the record would make a retry upload it again. The progress
 * record goes first, so a crash before the history insert still never re-uploads.
 */
internal suspend fun <T> recordSuccess(
    markDone: suspend () -> Unit,
    insertHistory: suspend () -> T,
    addTags: suspend () -> Unit,
): T = withContext(NonCancellable) {
    markDone()
    val result = insertHistory()
    addTags()
    result
}
