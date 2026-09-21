package com.meteomontana.android

import com.meteomontana.android.util.AppText
import java.io.File

/**
 * Da a los tests JVM los textos en español REALES de `res/values`, para que un
 * test pueda comprobar el mensaje que ve el usuario sin necesitar Robolectric.
 * Resuelve el id numérico → nombre con la clase generada `R.string` / `R.array`.
 */
object EsTextSource : AppText.Source {
    private val stringNames: Map<Int, String> =
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    private val arrayNames: Map<Int, String> =
        R.array::class.java.fields.associate { it.getInt(null) to it.name }

    private val xml: String by lazy {
        listOf("src/main/res/values", "app/src/main/res/values")
            .map { File(it) }.first { it.isDirectory }
            .listFiles { f -> f.name.endsWith(".xml") }!!
            .joinToString("\n") { it.readText() }
    }

    private fun unescape(s: String) = s
        .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    override fun string(id: Int, args: List<Any?>): String {
        val name = stringNames[id] ?: return "<res $id>"
        val raw = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: return "<$name>"
        val text = unescape(raw)
        return if (args.isEmpty()) text else text.format(*args.map { it ?: "" }.toTypedArray())
    }

    override fun array(id: Int): List<String> {
        val name = arrayNames[id] ?: return emptyList()
        val body = Regex("""<string-array name="$name">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: return emptyList()
        return Regex("<item>(.*?)</item>").findAll(body).map { unescape(it.groupValues[1]) }.toList()
    }

    fun install() { AppText.overrideSource = this }
}
