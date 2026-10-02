package com.xerahs.android.core.common.net

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactorTest {
    @Test fun redactsSensitiveHeaders() {
        assertEquals("Authorization: ██", LogRedactor.redact("Authorization: Bearer abc"))
        assertEquals("X-API-Key: ██", LogRedactor.redact("X-API-Key: 123"))
        assertEquals("x-amz-security-token: ██", LogRedactor.redact("x-amz-security-token: t"))
        assertEquals("Set-Cookie: ██", LogRedactor.redact("Set-Cookie: s=1"))
    }

    @Test fun keepsHarmlessHeaders() {
        assertEquals("Content-Type: image/png", LogRedactor.redact("Content-Type: image/png"))
        assertEquals("Keep-Alive: timeout=5", LogRedactor.redact("Keep-Alive: timeout=5"))
    }

    @Test fun redactsSensitiveQueryParametersInRequestLine() = assertEquals(
        "--> POST https://h.test/up?api_key=██&ttl=60&X-Amz-Signature=██",
        LogRedactor.redact("--> POST https://h.test/up?api_key=k1&ttl=60&X-Amz-Signature=sig")
    )
}
