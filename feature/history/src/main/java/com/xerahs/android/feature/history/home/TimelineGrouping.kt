package com.xerahs.android.feature.history.home

data class StampedId(val id: String, val timestamp: Long)

data class TimelineSection(val label: String, val ids: List<String>)

/** Groups timestamped ids into Today / Yesterday / Earlier buckets, newest first. */
object TimelineGrouping {
    private const val DAY = 24L * 60 * 60 * 1000

    fun group(items: List<StampedId>, now: Long): List<TimelineSection> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedByDescending { it.timestamp }
        val today = ArrayList<String>()
        val yesterday = ArrayList<String>()
        val earlier = ArrayList<String>()
        val startOfToday = now - (now % DAY)
        for (it in sorted) {
            when {
                it.timestamp >= startOfToday -> today += it.id
                it.timestamp >= startOfToday - DAY -> yesterday += it.id
                else -> earlier += it.id
            }
        }
        return listOf(
            "Today" to today, "Yesterday" to yesterday, "Earlier" to earlier,
        ).filter { it.second.isNotEmpty() }.map { TimelineSection(it.first, it.second) }
    }
}
