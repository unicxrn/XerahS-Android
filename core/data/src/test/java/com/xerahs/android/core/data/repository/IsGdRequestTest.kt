package com.xerahs.android.core.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class IsGdRequestTest {
    @Test fun buildsEncodedIsGdUrl() {
        val req = buildIsGdRequestUrl("https://i.xerahs.io/a b?x=1")
        assertTrue(req.startsWith("https://is.gd/create.php?format=simple&url="))
        assertTrue("must URL-encode the long url", req.contains("https%3A%2F%2Fi.xerahs.io%2Fa%20b") || req.contains("a+b"))
    }
}
