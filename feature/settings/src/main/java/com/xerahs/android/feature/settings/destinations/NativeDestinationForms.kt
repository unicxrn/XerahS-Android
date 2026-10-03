package com.xerahs.android.feature.settings.destinations

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination

data class FormField(
    val key: String,
    val label: String,
    val kind: Kind,
    val required: Boolean = true,
    val default: String = "",
    val help: String? = null,
) {
    enum class Kind { TEXT, URL, SECRET, SWITCH }
}

// Field tables for destinations whose settings are plain key/value forms.
object NativeDestinationForms {
    fun supports(destination: UploadDestination) = destination in setOf(
        UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST
    )

    fun fields(destination: UploadDestination): List<FormField> = when (destination) {
        UploadDestination.NEXTCLOUD -> listOf(
            FormField("serverUrl", "Server URL", FormField.Kind.URL, help = "For example https://cloud.example.com"),
            FormField("username", "Username", FormField.Kind.TEXT, help = "Your Nextcloud user ID (usually the login name)"),
            FormField("appPassword", "App password", FormField.Kind.SECRET, help = "Create one under Settings, Security, Devices & sessions"),
            FormField("folder", "Folder", FormField.Kind.TEXT, required = false, default = "XerahS"),
            FormField("publicShare", "Create a public share link", FormField.Kind.SWITCH, required = false, default = "true"),
        )
        UploadDestination.IMMICH -> listOf(
            FormField("serverUrl", "Server URL", FormField.Kind.URL, help = "For example https://photos.example.com"),
            FormField("apiKey", "API key", FormField.Kind.SECRET, help = "Create one under Account settings, API keys"),
            FormField("createShareLink", "Create a share link", FormField.Kind.SWITCH, required = false, default = "true"),
        )
        UploadDestination.GITHUB_GIST -> listOf(
            FormField("token", "Personal access token", FormField.Kind.SECRET, help = "Needs the gist scope"),
            FormField("isPublic", "Public gist", FormField.Kind.SWITCH, required = false, default = "false"),
        )
        else -> emptyList()
    }

    fun destinationOf(config: UploadConfig): UploadDestination? = when (config) {
        is UploadConfig.NextcloudConfig -> UploadDestination.NEXTCLOUD
        is UploadConfig.ImmichConfig -> UploadDestination.IMMICH
        is UploadConfig.GistConfig -> UploadDestination.GITHUB_GIST
        else -> null
    }

    fun toValues(config: UploadConfig): Map<String, String> = when (config) {
        is UploadConfig.NextcloudConfig -> mapOf(
            "serverUrl" to config.serverUrl, "username" to config.username, "appPassword" to config.appPassword,
            "folder" to config.folder, "publicShare" to config.publicShare.toString(),
        )
        is UploadConfig.ImmichConfig -> mapOf(
            "serverUrl" to config.serverUrl, "apiKey" to config.apiKey,
            "createShareLink" to config.createShareLink.toString(),
        )
        is UploadConfig.GistConfig -> mapOf("token" to config.token, "isPublic" to config.isPublic.toString())
        else -> emptyMap()
    }

    fun toConfig(destination: UploadDestination, values: Map<String, String>): UploadConfig {
        val v = fields(destination).associate { it.key to it.default } + values
        fun s(key: String) = v[key].orEmpty().trim()
        fun b(key: String) = v[key]?.toBooleanStrictOrNull() ?: false
        return when (destination) {
            UploadDestination.NEXTCLOUD -> UploadConfig.NextcloudConfig(s("serverUrl"), s("username"), s("appPassword"), s("folder"), b("publicShare"))
            UploadDestination.IMMICH -> UploadConfig.ImmichConfig(s("serverUrl"), s("apiKey"), b("createShareLink"))
            UploadDestination.GITHUB_GIST -> UploadConfig.GistConfig(s("token"), b("isPublic"))
            else -> throw IllegalArgumentException("$destination has no form")
        }
    }

    fun missingRequired(destination: UploadDestination, values: Map<String, String>): List<String> =
        fields(destination).filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
}
