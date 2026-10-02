package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.AfterUploadAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortenStepTest {
    private val shorten = setOf(AfterUploadAction.SHORTEN_URL)
    private val ok: suspend (String) -> Result<String> = { Result.success("https://s.test/1") }
    private val fail: suspend (String) -> Result<String> = { Result.failure(IllegalStateException("down")) }

    @Test fun shortensWhenSelected() = runBlocking { assertEquals("https://s.test/1", maybeShorten("https://long.test/a", shorten, ok)) }
    @Test fun keepsUrlWithoutAction() = runBlocking { assertEquals("https://long.test/a", maybeShorten("https://long.test/a", emptySet(), ok)) }
    @Test fun keepsUrlOnFailure() = runBlocking { assertEquals("https://long.test/a", maybeShorten("https://long.test/a", shorten, fail)) }
    @Test fun skipsNonHttp() = runBlocking { assertEquals("/data/x.png", maybeShorten("/data/x.png", shorten, ok)) }
}
