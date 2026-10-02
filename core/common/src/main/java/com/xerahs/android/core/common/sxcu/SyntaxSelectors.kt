package com.xerahs.android.core.common.sxcu

internal object JsonPath {
    fun select(json: String, path: String): String? = null
}

internal object XmlPath {
    fun select(xml: String, expression: String): String? = null
}

internal object RegexSelect {
    fun select(input: String, pattern: String, group: String?): String? = null
}
