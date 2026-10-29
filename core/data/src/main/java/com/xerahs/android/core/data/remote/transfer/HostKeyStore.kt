package com.xerahs.android.core.data.remote.transfer

import com.xerahs.android.core.data.local.datastore.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/** Pinned SSH host-key fingerprints (`SHA256:<base64>`), keyed by `host:port`. Not secrets. */
interface HostKeyStore {
    suspend fun get(hostPort: String): String?
    suspend fun put(hostPort: String, fingerprint: String)
    suspend fun remove(hostPort: String)
}

@Singleton
class DataStoreHostKeyStore internal constructor(private val pref: StringPref) : HostKeyStore {

    @Inject constructor(settings: SettingsDataStore) : this(object : StringPref {
        override suspend fun read(): String? = settings.getPinnedHostKeys()
        override suspend fun update(transform: (String?) -> String?) = settings.updatePinnedHostKeys(transform)
    })

    internal interface StringPref {
        suspend fun read(): String?
        suspend fun update(transform: (String?) -> String?)
    }

    override suspend fun get(hostPort: String): String? = decode(pref.read())[hostPort]

    override suspend fun put(hostPort: String, fingerprint: String) =
        pref.update { encode(decode(it) + (hostPort to fingerprint)) }

    override suspend fun remove(hostPort: String) =
        pref.update { encode(decode(it) - hostPort) }

    internal companion object {
        // One `hostPort=fingerprint` per line. Neither side can contain '=' or a newline:
        // host:port never does, and the fingerprint is unpadded base64.
        fun encode(map: Map<String, String>): String =
            map.entries.joinToString("\n") { "${it.key}=${it.value}" }

        fun decode(raw: String?): Map<String, String> =
            raw.orEmpty().lineSequence()
                .mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0 || i == line.lastIndex) null else line.substring(0, i) to line.substring(i + 1)
                }
                .toMap()
    }
}
