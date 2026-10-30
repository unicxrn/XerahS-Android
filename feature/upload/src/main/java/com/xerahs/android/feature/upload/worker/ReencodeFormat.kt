package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.ImageFormat

/** What a re-encoded upload is written as. */
internal enum class ReencodeFormat(val extension: String) {
    PNG("png"), JPEG("jpg"), WEBP("webp");

    companion object {
        /**
         * Original keeps PNG for PNG sources and for anything with transparency (a WebP with alpha,
         * say), since JPEG has no alpha; everything else stays JPEG as before.
         */
        fun choose(requested: ImageFormat, sourceMime: String?, hasAlpha: Boolean): ReencodeFormat = when (requested) {
            ImageFormat.WEBP -> WEBP
            ImageFormat.JPEG -> JPEG
            ImageFormat.ORIGINAL -> if (sourceMime == "image/png" || hasAlpha) PNG else JPEG
        }
    }
}
