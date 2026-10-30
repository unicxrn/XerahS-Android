package com.xerahs.android.feature.upload.screenshots

/** Pure bookkeeping for the screenshot watcher: lookback window and already-handled ids. */
internal object ScreenshotScan {
    const val LOOKBACK_SECONDS = 60L
    const val MAX_SEEN = 200

    /** DATE_ADDED lower bound (inclusive), so rows published late or in the same second are not lost. */
    fun queryFrom(lastCheck: Long): Long = (lastCheck - LOOKBACK_SECONDS).coerceAtLeast(0L)

    fun parseSeen(csv: String?): List<Long> = csv.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    fun encodeSeen(ids: List<Long>): String = ids.joinToString(",")

    fun mergeSeen(seen: List<Long>, handled: List<Long>): List<Long> = (seen + handled).distinct().takeLast(MAX_SEEN)

    fun isNew(id: Long, seen: Set<Long>): Boolean = id !in seen

    /** The newest DATE_ADDED among items that were handled; it never moves backwards. */
    fun nextCheck(lastCheck: Long, handledDates: List<Long>): Long = maxOf(lastCheck, handledDates.maxOrNull() ?: lastCheck)
}
