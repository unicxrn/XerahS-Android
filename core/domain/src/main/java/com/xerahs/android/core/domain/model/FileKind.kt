package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.file.MimeTypes

/** Coarse file type used to route uploads to a profile. */
enum class FileKind { IMAGE, TEXT, OTHER }

fun fileKindOf(mimeType: String?): FileKind = when {
    MimeTypes.isRasterImage(mimeType) -> FileKind.IMAGE
    MimeTypes.isText(mimeType) -> FileKind.TEXT
    else -> FileKind.OTHER
}
