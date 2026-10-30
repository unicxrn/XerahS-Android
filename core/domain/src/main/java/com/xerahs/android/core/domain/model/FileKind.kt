package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.file.MimeTypes

/** Coarse file type used to route uploads to a profile and to pick image link forms. */
enum class FileKind { IMAGE, TEXT, OTHER }

fun fileKindOf(mimeType: String?): FileKind = when {
    mimeType != null && mimeType.startsWith("image/") -> FileKind.IMAGE
    MimeTypes.isText(mimeType) -> FileKind.TEXT
    else -> FileKind.OTHER
}
