package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult

interface UploaderImportRepository {
    fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind
    fun parseSxcu(bytes: ByteArray): Result<ImportDraft>
    fun parseXsdc(bytes: ByteArray, passphrase: CharArray): Result<XsdcImportResult>
    /** Saves each draft as a new upload profile. Returns how many were saved. */
    suspend fun import(drafts: List<ImportDraft>): Int
}
