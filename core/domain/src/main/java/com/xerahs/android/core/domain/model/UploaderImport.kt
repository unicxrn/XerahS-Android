package com.xerahs.android.core.domain.model

enum class UploaderFileKind { SXCU, XSDC, UNKNOWN }

const val INSECURE_HTTP_WARNING = "Sends your files over unencrypted HTTP"

/** A destination config ready to be saved as an upload profile. */
data class ImportDraft(
    val name: String,
    val destination: UploadDestination,
    val config: UploadConfig,
    val warnings: List<String> = emptyList(),
    val makeDefault: Boolean = false,
)

data class XsdcImportResult(
    val drafts: List<ImportDraft>,
    /** Human-readable "Name (reason)" entries that could not be imported. */
    val skipped: List<String>,
)
