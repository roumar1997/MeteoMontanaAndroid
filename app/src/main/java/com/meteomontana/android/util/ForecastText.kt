package com.meteomontana.android.util

import com.meteomontana.android.R

/**
 * Textos que genera el SERVIDOR en español dentro de la previsión (el desglose
 * "¿Por qué este índice?" y la "mejor temporada"). Se traducen aquí al
 * mostrarlos; lo que no se reconoce se deja tal cual. Solo actúa en inglés.
 *
 * Es un parche del lado cliente a propósito: funciona ya contra el servidor de
 * producción. Lo limpio sería que el servidor redactara estos textos según la
 * cabecera Accept-Language, que las apps ya mandan.
 */
object ForecastText {

    private fun english() = AppText.get(R.string.lang_code) == "en"

    private val NAMES = mapOf(
        "TEMPERATURA" to "TEMPERATURE", "HUMEDAD" to "HUMIDITY", "VIENTO" to "WIND",
        "LLUVIA 24H" to "RAIN 24H", "LLUVIA 72H" to "RAIN 72H", "SEQUEDAD AIRE" to "AIR DRYNESS",
        "ROCA" to "ROCK",
    )

    /** "ROCA · GRANITO" → "ROCK · GRANITE"; "HUMEDAD" → "HUMIDITY". */
    fun factorName(name: String): String {
        if (!english()) return name
        NAMES[name]?.let { return it }
        if (name.startsWith("ROCA · ")) return "ROCK · " + CatalogLabels.rock(name.removePrefix("ROCA · ")).uppercase()
        return name
    }

    private val WARM = Regex("""^Aún templada \((\d+)°\): guarda el calor (~\d+ h) tras el sol$""")
    private val COLD = Regex("""^Fría \((\d+)°\): buena fricción · se enfría en (~\d+ h)$""")
    private val MILD = Regex("""^Templada \((\d+)°\) · inercia (~\d+ h)$""")

    fun factorDisplay(display: String): String {
        if (!english()) return display
        when (display) {
            "Roca seca" -> return "Dry rock"
            "Roca húmeda" -> return "Damp rock"
        }
        WARM.find(display)?.let { return "Still warm (${it.groupValues[1]}°): holds the heat ${it.groupValues[2]} after sun" }
        COLD.find(display)?.let { return "Cold (${it.groupValues[1]}°): good friction · cools down in ${it.groupValues[2]}" }
        MILD.find(display)?.let { return "Mild (${it.groupValues[1]}°) · inertia ${it.groupValues[2]}" }
        return display
    }

    private val MONTHS_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** "Enero-Febrero" → "January-February" (nombres de mes dentro de un texto del servidor). */
    fun monthsIn(text: String): String {
        if (!english()) return text
        var out = text
        val long = CalendarLabels.monthsLong()
        MONTHS_ES.forEachIndexed { i, es ->
            out = out.replace(Regex(es, RegexOption.IGNORE_CASE), long[i].replaceFirstChar { it.uppercase() })
        }
        return out
    }
}
