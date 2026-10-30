package com.xerahs.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindTest {
    @Test fun kinds() {
        assertEquals(FileKind.IMAGE, fileKindOf("image/png"))
        assertEquals(FileKind.TEXT, fileKindOf("text/plain"))
        assertEquals(FileKind.TEXT, fileKindOf("application/json"))
        assertEquals(FileKind.OTHER, fileKindOf("video/mp4"))
        assertEquals(FileKind.OTHER, fileKindOf(null))
        assertEquals(FileKind.TEXT, fileKindOf("text/csv"))
        assertEquals(FileKind.TEXT, fileKindOf("application/xml"))
        assertEquals(FileKind.IMAGE, fileKindOf("image/svg+xml"))
    }
}
