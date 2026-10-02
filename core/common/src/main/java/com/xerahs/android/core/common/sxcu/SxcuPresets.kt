package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonPrimitive

data class PresetField(val key: String, val label: String, val secret: Boolean = false)

data class SxcuPreset(val id: String, val name: String, val description: String, val fields: List<PresetField>)

// Bundled .sxcu templates. {{key}} placeholders are filled from user input before parsing.
object SxcuPresets {
    val all = listOf(
        SxcuPreset("xbackbone", "XBackBone", "Self-hosted file host",
            listOf(PresetField("host", "Server URL"), PresetField("token", "Upload token", secret = true))),
        SxcuPreset("pastebin", "Pastebin", "Text pastes",
            listOf(PresetField("api_key", "Developer API key", secret = true))),
        SxcuPreset("bitly", "Bitly", "URL shortener",
            listOf(PresetField("token", "Access token", secret = true))),
    )

    fun render(id: String, values: Map<String, String>): Result<CustomUploaderSpec> = runCatching {
        val preset = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown preset $id")
        var text = javaClass.getResourceAsStream("/sxcu-presets/$id.sxcu")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Missing preset file $id")
        preset.fields.forEach { field ->
            val raw = values[field.key].orEmpty().trim()
            val value = if (field.key == "host") {
                val trimmed = raw.trimEnd('/')
                if ("://" in trimmed) trimmed else "https://$trimmed"
            } else raw
            text = text.replace("{{${field.key}}}", jsonEscape(value))
        }
        SxcuParser.parse(text).getOrThrow()
    }

    private fun jsonEscape(s: String) = JsonPrimitive(s).toString().let { it.substring(1, it.length - 1) }
}
