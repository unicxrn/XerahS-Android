package com.xerahs.android.core.data.remote.transfer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostKeyStoreTest {
    private class MemoryPref : DataStoreHostKeyStore.StringPref {
        var value: String? = null
        override suspend fun read(): String? = value
        override suspend fun update(transform: (String?) -> String?) { value = transform(value) }
    }

    private val pref = MemoryPref()
    private val store = DataStoreHostKeyStore(pref)

    @Test fun putGetRemove() = runBlocking {
        assertNull(store.get("example.com:22"))
        store.put("example.com:22", "SHA256:abc")
        store.put("[::1]:2222", "SHA256:def/+")
        assertEquals("SHA256:abc", store.get("example.com:22"))
        assertEquals("SHA256:def/+", store.get("[::1]:2222"))
        store.remove("example.com:22")
        assertNull(store.get("example.com:22"))
        assertEquals("SHA256:def/+", store.get("[::1]:2222"))
    }

    @Test fun putReplacesExisting() = runBlocking {
        store.put("h:22", "SHA256:old")
        store.put("h:22", "SHA256:new")
        assertEquals("SHA256:new", store.get("h:22"))
    }

    @Test fun putIfAbsentKeepsFirstValue() = runBlocking {
        assertNull(store.putIfAbsent("h:22", "SHA256:first"))
        assertEquals("SHA256:first", store.putIfAbsent("h:22", "SHA256:second"))
        assertEquals("SHA256:first", store.get("h:22"))
    }

    @Test fun codecRoundTrips() {
        val map = mapOf("a:22" to "SHA256:x", "b:2022" to "SHA256:y")
        assertEquals(map, DataStoreHostKeyStore.decode(DataStoreHostKeyStore.encode(map)))
        assertEquals(emptyMap<String, String>(), DataStoreHostKeyStore.decode(null))
        assertEquals(emptyMap<String, String>(), DataStoreHostKeyStore.decode("garbage\n\n"))
    }
}
