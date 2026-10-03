package com.xerahs.android.core.domain.model

enum class AfterUploadAction(val label: String) {
    COPY_URL("Copy URL"),
    SHORTEN_URL("Shorten URL"),
    SHARE_SHEET("Share"),
    OPEN_URL("Open in browser");

    companion object {
        fun encode(actions: Set<AfterUploadAction>): String =
            entries.filter { it in actions }.joinToString(",") { it.name }

        fun decode(value: String?): Set<AfterUploadAction> =
            value.orEmpty().split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()
    }
}
