package com.xerahs.android.core.common.sxcu

enum class CustomDestinationType(val sxcuName: String) {
    IMAGE("ImageUploader"),
    TEXT("TextUploader"),
    FILE("FileUploader"),
    URL_SHORTENER("URLShortener");

    companion object {
        fun fromSxcu(name: String): CustomDestinationType? =
            entries.firstOrNull { it.sxcuName.equals(name.trim(), ignoreCase = true) }
    }
}

enum class CustomBodyType(val sxcuName: String) {
    NONE("None"),
    MULTIPART_FORM_DATA("MultipartFormData"),
    FORM_URL_ENCODED("FormURLEncoded"),
    JSON("JSON"),
    XML("XML"),
    BINARY("Binary");

    companion object {
        fun fromSxcu(name: String): CustomBodyType? =
            entries.firstOrNull { it.sxcuName.equals(name.trim(), ignoreCase = true) }
    }
}

/** Full ShareX custom uploader (.sxcu) definition. Template fields may contain ShareX syntax. */
data class CustomUploaderSpec(
    val name: String = "Custom uploader",
    val destinationTypes: Set<CustomDestinationType> =
        setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE),
    val requestMethod: String = "POST",
    val requestURL: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: CustomBodyType = CustomBodyType.MULTIPART_FORM_DATA,
    val arguments: Map<String, String> = emptyMap(),
    val fileFormName: String = "file",
    val data: String = "",
    val url: String = "",
    val thumbnailURL: String = "",
    val deletionURL: String = "",
    val errorMessage: String = "",
) {
    /** Templates evaluated before the request is sent (where {inputbox} prompts can appear). */
    fun requestTemplates(): List<String> =
        listOf(requestURL, data) + parameters.values + headers.values + arguments.values
}
