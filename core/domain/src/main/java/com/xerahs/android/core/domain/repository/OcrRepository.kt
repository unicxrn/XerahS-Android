package com.xerahs.android.core.domain.repository

interface OcrRepository {
    /** Recognize text in the image at [imagePath]. Returns the joined text (may be empty), or Result.failure. */
    suspend fun recognizeText(imagePath: String): Result<String>
}
