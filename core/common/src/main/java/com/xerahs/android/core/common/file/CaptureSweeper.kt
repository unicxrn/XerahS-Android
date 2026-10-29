package com.xerahs.android.core.common.file

import java.io.File

/** Removes old `pick_*` and `shared_*` copy folders from the captures directory. */
object CaptureSweeper {
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Deletes folders older than [maxAgeMs] unless one of [protectedPaths] lies inside them.
     * Returns how many folders were deleted.
     */
    fun sweep(capturesDir: File, now: Long, protectedPaths: Set<String>, maxAgeMs: Long = MAX_AGE_MS): Int {
        val dirs = capturesDir.listFiles { f -> f.isDirectory && (f.name.startsWith("pick_") || f.name.startsWith("shared_")) }
            ?: return 0
        var deleted = 0
        for (dir in dirs) {
            if (now - dir.lastModified() < maxAgeMs) continue
            val prefix = dir.absolutePath + File.separator
            if (protectedPaths.any { it.startsWith(prefix) }) continue
            if (dir.deleteRecursively()) deleted++
        }
        return deleted
    }
}
