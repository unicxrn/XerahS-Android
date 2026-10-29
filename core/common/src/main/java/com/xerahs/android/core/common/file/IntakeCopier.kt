package com.xerahs.android.core.common.file

import java.io.File

/**
 * Copies every picked or shared item into app storage first, then reports the files via [onReady].
 * Navigation happens only inside [onReady], so a content URI is never read after this returns.
 * Items that fail to copy are dropped; [onReady] is not called when none succeed ([onFailed] is).
 */
class IntakeCopier<T>(private val copy: suspend (T) -> File) {
    suspend fun intake(items: List<T>, onReady: (List<File>) -> Unit, onFailed: () -> Unit = {}) {
        val files = items.mapNotNull { runCatching { copy(it) }.getOrNull() }
        if (files.isEmpty()) onFailed() else onReady(files)
    }
}
