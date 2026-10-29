package com.xerahs.android.core.domain.model

enum class BiometricLockMode {
    OFF,
    LOCK_APP,
    LOCK_CREDENTIALS;

    companion object {
        /** Parses the stored name, falling back to [OFF] for anything unknown. */
        fun fromName(name: String?): BiometricLockMode =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}
