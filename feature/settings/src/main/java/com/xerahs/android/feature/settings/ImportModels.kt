package com.xerahs.android.feature.settings

enum class FieldResolution {
    KEEP_CURRENT,
    USE_IMPORTED
}

data class ImportField(
    val key: String,
    val label: String,
    val currentValue: String,
    val importedValue: String,
    val hasConflict: Boolean,
    var resolution: FieldResolution = if (hasConflict) FieldResolution.KEEP_CURRENT else FieldResolution.USE_IMPORTED
)

data class ImportSection(
    val title: String,
    val fields: List<ImportField>
)

data class ImportPreview(
    val sections: List<ImportSection>
) {
    val conflictCount: Int
        get() = sections.sumOf { section -> section.fields.count { it.hasConflict } }

    val totalFields: Int
        get() = sections.sumOf { it.fields.size }

    fun setAllResolutions(resolution: FieldResolution) {
        sections.forEach { section ->
            section.fields.forEach { field ->
                if (field.hasConflict) {
                    field.resolution = resolution
                }
            }
        }
    }
}
