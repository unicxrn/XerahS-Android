package com.xerahs.android.core.common.file

import java.io.File

/** Removes old `pick_*` and `shared_*` copy folders from the captures directory, and old tool output from the cache. */
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

    const val TOOLS_OUT = "tools_out"
    const val TOOLS_COMBINE = "tools_combine"
    const val TOOLS_BEAUTIFY = "tools_beautify"
    const val TOOL_MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Clears tool leftovers: rendered files in cacheDir/tools_out (unless listed in
     * [protectedPaths]) and per-screen copy folders in tools_combine and tools_beautify, once
     * older than [maxAgeMs]. With [exportsDir], the editor's exported files there go too once
     * older than [exportsMaxAgeMs], unless protected. Returns how many files and folders were deleted.
     */
    fun sweepToolCache(
        cacheDir: File,
        now: Long,
        protectedPaths: Set<String>,
        maxAgeMs: Long = TOOL_MAX_AGE_MS,
        exportsDir: File? = null,
        exportsMaxAgeMs: Long = MAX_AGE_MS,
    ): Int {
        var deleted = sweepFiles(File(cacheDir, TOOLS_OUT), now, protectedPaths, maxAgeMs)
        for (name in listOf(TOOLS_COMBINE, TOOLS_BEAUTIFY)) {
            File(cacheDir, name).listFiles { f -> f.isDirectory }?.forEach { dir ->
                if (now - dir.lastModified() < maxAgeMs) return@forEach
                val prefix = dir.absolutePath + File.separator
                if (protectedPaths.any { it.startsWith(prefix) }) return@forEach
                if (dir.deleteRecursively()) deleted++
            }
        }
        if (exportsDir != null) deleted += sweepFiles(exportsDir, now, protectedPaths, exportsMaxAgeMs)
        return deleted
    }

    private fun sweepFiles(dir: File, now: Long, protectedPaths: Set<String>, maxAgeMs: Long): Int {
        var deleted = 0
        dir.listFiles { f -> f.isFile }?.forEach { f ->
            if (now - f.lastModified() >= maxAgeMs && f.absolutePath !in protectedPaths && f.delete()) deleted++
        }
        return deleted
    }
}
