package com.xerahs.android.core.data.repository

import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.data.importer.UploaderImportParsing
import com.xerahs.android.core.data.importer.XsdcDecoder
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.core.domain.repository.UploaderImportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploaderImportRepositoryImpl @Inject constructor(
    private val profileRepository: UploadProfileRepository,
) : UploaderImportRepository {

    override fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind =
        UploaderImportParsing.detect(bytes, fileName)

    override fun parseSxcu(bytes: ByteArray): Result<ImportDraft> = UploaderImportParsing.sxcuDraft(bytes)

    override fun parseXsdc(bytes: ByteArray, passphrase: CharArray): Result<XsdcImportResult> =
        runCatching { UploaderImportParsing.xsdcDrafts(XsdcDecoder.decode(bytes, passphrase)) }

    override suspend fun import(drafts: List<ImportDraft>): Int {
        drafts.forEach { draft ->
            val profile = UploadProfile(
                id = generateId(),
                name = draft.name,
                destination = draft.destination,
                isDefault = draft.makeDefault,
                createdAt = generateTimestamp(),
            )
            profileRepository.createProfile(profile, draft.config)
            if (draft.makeDefault) profileRepository.setDefault(profile.id, draft.destination)
        }
        return drafts.size
    }
}
