package com.xerahs.android.feature.history.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineGroupingTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1781870400000L // fixed reference instant

    @Test fun groupsTodayYesterdayEarlier() {
        val startOfToday = now - (now % dayMs)
        val items = listOf(
            StampedId("today", startOfToday + 1000),
            StampedId("yest", startOfToday - 1000),
            StampedId("old", startOfToday - 5 * dayMs),
        )
        val sections = TimelineGrouping.group(items, now)
        assertEquals(listOf("Today", "Yesterday", "Earlier"), sections.map { it.label })
        assertEquals(listOf("today"), sections[0].ids)
        assertEquals(listOf("yest"), sections[1].ids)
        assertEquals(listOf("old"), sections[2].ids)
    }

    @Test fun newestFirstWithinSection() {
        val startOfToday = now - (now % dayMs)
        val items = listOf(
            StampedId("a", startOfToday + 1000),
            StampedId("b", startOfToday + 5000),
        )
        val sections = TimelineGrouping.group(items, now)
        assertEquals(listOf("b", "a"), sections[0].ids)
    }

    @Test fun emptyInputProducesNoSections() {
        assertEquals(emptyList<TimelineSection>(), TimelineGrouping.group(emptyList(), now))
    }
}
