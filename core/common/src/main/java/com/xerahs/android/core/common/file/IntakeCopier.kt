package com.xerahs.android.core.common.file

import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Copies every picked or shared item into app storage first, then reports the files and how many
 * failed via [onReady]. Navigation happens only inside [onReady], so a content URI is never read
 * after this returns. When nothing copies, [onFailed] gets the first error instead.
 */
class IntakeCopier<T>(private val copy: suspend (T) -> File) {
    suspend fun intake(
        items: List<T>,
        onReady: (files: List<File>, failedCount: Int) -> Unit,
        onFailed: (Throwable?) -> Unit = {}
    ) {
        var firstError: Throwable? = null
        val files = items.mapNotNull {
            try {
                copy(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                null
            }
        }
        if (files.isEmpty()) onFailed(firstError) else onReady(files, items.size - files.size)
    }
}
