package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

/** Minimal JSONPath: `a.b`, `a[0].b`, `$.a`, `a['key with dots']`. */
internal object JsonPath {
    private val TOKEN = Regex("""\[(\d+)]|\[['"]([^'"]+)['"]]|([^.\[\]]+)""")

    fun select(json: String, path: String): String? {
        return try {
            var el: JsonElement = JsonParser.parseString(json)
            val p = path.trim().removePrefix("$").removePrefix(".")
            for (m in TOKEN.findAll(p)) {
                val index = m.groupValues[1]
                el = if (index.isNotEmpty()) {
                    val arr = el as? JsonArray ?: return null
                    val i = index.toInt()
                    if (i >= arr.size()) return null
                    arr[i]
                } else {
                    val key = m.groupValues[2].ifEmpty { m.groupValues[3] }
                    (el as? JsonObject)?.get(key) ?: return null
                }
            }
            when {
                el.isJsonNull -> null
                el.isJsonPrimitive -> el.asString
                else -> el.toString()
            }
        } catch (e: Exception) {
            null
        }
    }
}

internal object XmlPath {
    fun select(xml: String, expression: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            XPathFactory.newInstance().newXPath().evaluate(expression, doc).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

internal object RegexSelect {
    fun select(input: String, pattern: String, group: String?): String? {
        val match = Regex(pattern).find(input) ?: return null
        if (group.isNullOrBlank()) return match.value
        group.trim().toIntOrNull()?.let { return match.groups[it]?.value }
        return (match.groups as? MatchNamedGroupCollection)?.get(group.trim())?.value
    }
}
