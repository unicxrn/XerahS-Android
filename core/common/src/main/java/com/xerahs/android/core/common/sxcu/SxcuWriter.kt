package com.xerahs.android.core.common.sxcu

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

/** Serialises a [CustomUploaderSpec] to ShareX .sxcu JSON (also our storage format). */
object SxcuWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun write(spec: CustomUploaderSpec): String {
        val o = JsonObject()
        o.addProperty("Version", "17.0.0")
        o.addProperty("Name", spec.name)
        o.addProperty(
            "DestinationType",
            spec.destinationTypes.sortedBy { it.ordinal }.joinToString(", ") { it.sxcuName }
        )
        o.addProperty("RequestMethod", spec.requestMethod)
        o.addProperty("RequestURL", spec.requestURL)
        if (spec.parameters.isNotEmpty()) o.add("Parameters", spec.parameters.toJson())
        if (spec.headers.isNotEmpty()) o.add("Headers", spec.headers.toJson())
        o.addProperty("Body", spec.body.sxcuName)
        if (spec.arguments.isNotEmpty()) o.add("Arguments", spec.arguments.toJson())
        if (spec.fileFormName.isNotEmpty()) o.addProperty("FileFormName", spec.fileFormName)
        if (spec.data.isNotEmpty()) o.addProperty("Data", spec.data)
        listOf(
            "URL" to spec.url, "ThumbnailURL" to spec.thumbnailURL,
            "DeletionURL" to spec.deletionURL, "ErrorMessage" to spec.errorMessage,
        ).filter { it.second.isNotEmpty() }.forEach { (k, v) -> o.addProperty(k, v) }
        return gson.toJson(o)
    }

    private fun Map<String, String>.toJson() = JsonObject().also { o -> forEach { (k, v) -> o.addProperty(k, v) } }
}
