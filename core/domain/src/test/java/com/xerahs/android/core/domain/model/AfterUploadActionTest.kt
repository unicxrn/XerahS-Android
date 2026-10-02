package com.xerahs.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AfterUploadActionTest {
    @Test fun encodesInDeclarationOrder() = assertEquals(
        "COPY_URL,OPEN_URL",
        AfterUploadAction.encode(setOf(AfterUploadAction.OPEN_URL, AfterUploadAction.COPY_URL))
    )

    @Test fun decodesAndIgnoresUnknownNames() = assertEquals(
        setOf(AfterUploadAction.SHORTEN_URL, AfterUploadAction.SHARE_SHEET),
        AfterUploadAction.decode("SHORTEN_URL, SHARE_SHEET,BOGUS")
    )

    @Test fun emptyAndNullDecodeToEmpty() {
        assertEquals(emptySet<AfterUploadAction>(), AfterUploadAction.decode(""))
        assertEquals(emptySet<AfterUploadAction>(), AfterUploadAction.decode(null))
    }
}
