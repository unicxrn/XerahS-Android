package com.xerahs.android.core.domain.model

enum class BiometricLockMode {
    OFF,
    LOCK_APP,
    LOCK_CREDENTIALS;

    companion object {
        /**
         * Parses the stored name. Blank means OFF; an unknown value fails closed to
         * [LOCK_APP] so a corrupt or newer setting never silently disables the lock.
         */
        fun fromName(name: String?): BiometricLockMode {
            if (name.isNullOrBlank()) return OFF
            return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: LOCK_APP
        }
    }
}
