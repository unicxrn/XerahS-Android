package com.xerahs.android.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureSweeperTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 24L * 60 * 60 * 1000

    private fun dir(root: File, name: String, ageDays: Long, now: Long): File =
        File(root, name).apply { mkdirs(); File(this, "f.png").writeText("x"); setLastModified(now - ageDays * day) }

    @Test fun deletesOnlyOldPickAndSharedFolders() {
        val root = tmp.newFolder(); val now = System.currentTimeMillis()
        val oldPick = dir(root, "pick_1", 8, now)
        val oldShared = dir(root, "shared_1", 9, now)
        val fresh = dir(root, "pick_2", 1, now)
        val other = dir(root, "keep_me", 30, now)
        assertEquals(2, CaptureSweeper.sweep(root, now, emptySet()))
        assertFalse(oldPick.exists()); assertFalse(oldShared.exists())
        assertTrue(fresh.exists()); assertTrue(other.exists())
    }

    @Test fun skipsFoldersWithProtectedFiles() {
        val root = tmp.newFolder(); val now = System.currentTimeMillis()
        val old = dir(root, "shared_1", 20, now)
        assertEquals(0, CaptureSweeper.sweep(root, now, setOf(File(old, "f.png").absolutePath)))
        assertTrue(old.exists())
    }

    private fun file(root: File, name: String, ageMs: Long, now: Long): File =
        File(root, name).apply { parentFile?.mkdirs(); writeText("x"); setLastModified(now - ageMs) }

    @Test fun toolSweepDeletesOldOutputsButKeepsFreshAndReferenced() {
        val cache = tmp.newFolder(); val now = System.currentTimeMillis()
        val out = File(cache, CaptureSweeper.TOOLS_OUT)
        val old = file(out, "combined_1.jpg", 2 * day, now)
        val referenced = file(out, "combined_2.png", 3 * day, now)
        val fresh = file(out, "combined_3.png", day / 2, now)
        assertEquals(1, CaptureSweeper.sweepToolCache(cache, now, setOf(referenced.absolutePath)))
        assertFalse(old.exists()); assertTrue(referenced.exists()); assertTrue(fresh.exists())
    }

    @Test fun toolSweepDeletesOrphanedCombineFolders() {
        val cache = tmp.newFolder(); val now = System.currentTimeMillis()
        val root = File(cache, CaptureSweeper.TOOLS_COMBINE)
        val orphan = dir(root, "a1", 2, now)
        val live = File(root, "b2").apply { mkdirs(); File(this, "f.png").writeText("x") }
        assertEquals(1, CaptureSweeper.sweepToolCache(cache, now, emptySet()))
        assertFalse(orphan.exists()); assertTrue(live.exists())
    }

    @Test fun toolSweepToleratesMissingFolders() {
        assertEquals(0, CaptureSweeper.sweepToolCache(tmp.newFolder(), System.currentTimeMillis(), emptySet()))
    }
}
