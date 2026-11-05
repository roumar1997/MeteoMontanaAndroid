package com.meteomontana.android.presentation.assistant

import com.meteomontana.android.presentation.assistant.AssistantPresenter.HitGroup
import com.meteomontana.android.presentation.assistant.AssistantPresenter.HitSector
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantClarification
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantRestriction
import com.meteomontana.android.domain.model.AssistantRestrictions
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.usecase.journal.Achievement
import com.meteomontana.android.domain.usecase.journal.GoalProgress
import com.meteomontana.android.domain.model.AssistantSector
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantStone
import com.meteomontana.android.domain.model.AssistantSummary
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.assistant.MineAnswerer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Textos del asistente sobre el tiempo, mensajes guardados y cabeceras de recomendación y desglose (extensiones de AssistantPresenter).

// ── El tiempo ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Una décima, con coma en español y punto en inglés. */
fun AssistantPresenter.oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val s = (if (tenths < 0) "-" else "") + "${kotlin.math.abs(tenths) / 10}.${kotlin.math.abs(tenths) % 10}"
    return if (en) s else s.replace('.', ',')
}

fun AssistantPresenter.weatherTitle(w: AssistantWeather): String = w.placeName ?: t("Tu ubicación", "Your location")

/** "15h" a partir de "2026-10-08T15:00". */
fun AssistantPresenter.hourLabel(iso: String): String {
    val time = iso.substringAfterLast('T', iso)
    return if (time.length >= 2) "${time.take(2)}h" else iso
}

/** Altura de la barra de lluvia de una hora: 3 mm o más llenan la barra; un poco de lluvia siempre se ve. */
fun AssistantPresenter.rainBarHeight(mm: Double, full: Float): Float {
    if (mm <= 0) return 0f
    return maxOf(3f, minOf(full, (mm / 3.0).toFloat() * full))
}

fun AssistantPresenter.windLabel(kmh: Double): String = when {
    kmh < 12 -> t("flojo", "light")
    kmh < 25 -> t("moderado", "moderate")
    kmh < 40 -> t("fuerte", "strong")
    else -> t("muy fuerte", "very strong")
}

/**
 * Responde a "¿va a llover?" con un SÍ o un NO claro, la hora y la probabilidad: "No, no se espera lluvia hasta las
 * 23h (probabilidad máxima 0 %)." / "Sí, va a llover: empezará sobre las 17h…".
 */
fun AssistantPresenter.rainHeadline(w: AssistantWeather): String {
    val r = w.rain
    val hours = r.hoursChecked
    val prob = r.maxProbability
    val until = r.until?.let { hourLabel(it) }
    val start = r.startsInHours
    if (!r.expected || start == null) {
        return if (until != null) t("No, no se espera lluvia hasta las $until. La probabilidad máxima es del $prob %.",
            "No, no rain expected until $until. The maximum chance is $prob %.")
        else t("No, no se espera lluvia en las próximas $hours h. La probabilidad máxima es del $prob %.",
            "No, no rain expected in the next $hours h. The maximum chance is $prob %.")
    }
    val mm = oneDecimal(r.totalMm)
    if (start == 0) {
        return if (until != null) t("Sí, está lloviendo ahora o va a empezar ya (≈$mm mm hasta las $until).",
            "Yes, it's raining now or about to (≈$mm mm until $until).")
        else t("Sí, está lloviendo ahora o va a empezar ya (≈$mm mm en las próximas $hours h).",
            "Yes, it's raining now or about to (≈$mm mm in the next $hours h).")
    }
    val at = r.startsAt?.let { hourLabel(it) }
    if (at != null && until != null) {
        return t("Sí, va a llover: empezará sobre las $at (en ~$start h). Caerán ≈$mm mm hasta las $until, con hasta un $prob % de probabilidad.",
            "Yes, it will rain: starting around $at (in ~$start h). About $mm mm until $until, with up to $prob % chance.")
    }
    return t("Sí, va a llover: empezará en ~$start h (≈$mm mm en las próximas $hours h, hasta un $prob % de probabilidad).",
        "Yes, it will rain: starting in ~$start h (≈$mm mm in the next $hours h, up to $prob % chance).")
}

private fun AssistantPresenter.round(d: Double): Int = d.roundToInt()

/** Mínima y máxima de cada día pedido, una línea por día: "VIE 9: 5 – 14 °C". */
fun AssistantPresenter.temperatureByDay(w: AssistantWeather): String {
    val lines = w.days.map { d -> "${dayLabel(d.date)}: ${round(d.tempMin)} – ${round(d.tempMax)} °C" }
    return (listOf(weatherTitle(w) + ":") + lines).joinToString("\n")
}

/** La frase del asistente sobre el tiempo, según lo que se preguntó. */
fun AssistantPresenter.weatherHeadline(w: AssistantWeather): String {
    // "Temperaturas mínimas y máximas hoy, mañana y pasado": una línea por día, no el "ahora" ni las horas de hoy.
    if (w.topic == "TEMPERATURE" && w.days.isNotEmpty()) return temperatureByDay(w)
    val place = weatherTitle(w)
    val n = w.now
    return when (w.topic) {
        "RAIN" -> "$place: ${rainHeadline(w)}"
        "WIND" -> {
            val peak = maxOf(n.windKmh, w.hours.maxOfOrNull { it.windKmh } ?: n.windKmh)
            t("$place: viento ahora ${round(n.windKmh)} km/h (${windLabel(n.windKmh)}). Máximo en las próximas horas: ${round(peak)} km/h.",
                "$place: wind now ${round(n.windKmh)} km/h (${windLabel(n.windKmh)}). Peak in the next hours: ${round(peak)} km/h.")
        }
        "HUMIDITY" -> {
            var text = t("$place: humedad ${round(n.humidity)} %.", "$place: humidity ${round(n.humidity)} %.")
            n.dewPoint?.let { text += " " + t("Punto de rocío: ${round(it)} °C.", "Dew point: ${round(it)} °C.") }
            text += " " + if (w.climbing.rockWet) t("La roca está húmeda.", "The rock is damp.") else t("La roca está seca.", "The rock is dry.")
            text
        }
        "ROCK" ->
            if (w.climbing.rockWet) {
                val drying = w.climbing.dryingMessage.orEmpty()
                t("$place: la roca está húmeda.", "$place: the rock is damp.") + (if (drying.isEmpty()) "" else " $drying")
            } else t("$place: la roca está seca, se puede escalar.", "$place: the rock is dry, you can climb.")
        "TEMPERATURE" -> {
            val temps = w.hours.map { it.temperature }
            val low = round(temps.minOrNull() ?: n.temperature)
            val high = round(temps.maxOrNull() ?: n.temperature)
            t("$place: ahora hay ${round(n.temperature)} °C. En las próximas horas, entre $low y $high °C.",
                "$place: it's ${round(n.temperature)} °C now. In the next hours, between $low and $high °C.")
        }
        else -> t("$place: ahora ${round(n.temperature)} °C, viento ${round(n.windKmh)} km/h, humedad ${round(n.humidity)} %. Para escalar: ${w.climbing.label} (${w.climbing.score}/100).",
            "$place: now ${round(n.temperature)} °C, wind ${round(n.windKmh)} km/h, humidity ${round(n.humidity)} %. For climbing: ${w.climbing.label} (${w.climbing.score}/100).")
    }
}

// ── Mensajes recuperados ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Aviso de cuánto hace que se enseñó una tarjeta guardada ("Guardado hace 3 h. El tiempo puede haber cambiado.").
 * null si hace menos de media hora: ahí no hace falta decir nada.
 */
fun AssistantPresenter.savedNote(createdAtMs: Long, nowMs: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): String? {
    val seconds = (nowMs - createdAtMs) / 1000.0
    if (seconds < 1800) return null
    val hours = maxOf(1, (seconds / 3600).roundToLong().toInt())
    return t("Guardado hace $hours h. El tiempo puede haber cambiado.", "Saved $hours h ago. The weather may have changed.")
}

// ── Cabeceras de los resultados ───────────────────────────────────────────────────────────────────────────────

fun AssistantPresenter.recommendationIntro(r: AssistantRecommendation): String {
    if (r.schools.isEmpty()) {
        return if (r.filtered) t("No he encontrado escuelas con vías de ese grado en la zona.", "I found no crags with routes of that grade nearby.")
        else t("No he encontrado escuelas en la zona.", "I couldn't find any schools in the area.")
    }
    // Pedido por cantidad ("las escuelas con más 7a"): no es una falta de previsión.
    if (r.byCount) return t("Las escuelas con más vías o bloques de ese grado:", "The schools with the most routes or boulders of that grade:")
    if (!r.forecastAvailable) {
        return if (r.filtered) t("Aún no hay previsión para esas fechas. Te ordeno las escuelas por número de vías de ese grado.",
            "There's no forecast for those dates yet. I'm ranking crags by number of routes of that grade.")
        else t("Aún no hay previsión para esas fechas. Te ordeno las escuelas por número de vías.",
            "There's no forecast for those dates yet. I'm ranking the schools by number of routes.")
    }
    // Una sola escuela y varios días ("¿qué día voy a Albarracín?"): se dice cuál es el mejor.
    val only = r.schools.singleOrNull()
    if (only != null && r.dates.size >= 2) {
        val dry = only.days.filter { !it.rainy }
        val best = (if (dry.isEmpty()) only.days else dry).maxByOrNull { it.score }
        if (best != null) return t("En ${only.name} el mejor día es ${dayLabel(best.date)} (${best.score}/100).",
            "In ${only.name} the best day is ${dayLabel(best.date)} (${best.score}/100).")
    }
    val first = r.dates.firstOrNull() ?: return t("Esta es la mejor opción:", "This is the best option:")
    val range = dateRange(first, r.dates.lastOrNull())
    return t("Para $range, esta es la mejor opción:", "For $range, this is the best option:")
}

fun AssistantPresenter.breakdownIntro(b: AssistantBreakdown): String {
    if (b.totalStones == 0) return t("No he encontrado piedras que cumplan eso en ${b.school.name}.", "I found no boulders matching that at ${b.school.name}.")
    val known = t("De ${b.totalStones} piedras, ${b.stonesWithOrientation} tienen la orientación votada.",
        "Of ${b.totalStones} boulders, ${b.stonesWithOrientation} have a voted orientation.")
    val filtered = b.sun != null || b.sectors.any { it.unknownOrientation > 0 }
    return if (filtered) known else t("Estas son las piedras de ${b.school.name} por sector.", "These are the boulders at ${b.school.name} by sector.")
}

/** Resumen corto de un sector: "2 a la sombra · 1 sin orientación". */
fun AssistantPresenter.sectorSummary(s: AssistantSector, sun: String?): String {
    val parts = mutableListOf<String>()
    val matching = s.matching
    parts += when (sun) {
        "SHADE" -> t("$matching a la sombra", "$matching in the shade")
        "SUN" -> t("$matching al sol", "$matching in the sun")
        else -> t("$matching piedras", "$matching boulders")
    }
    if (s.unknownOrientation > 0) parts += t("${s.unknownOrientation} sin orientación", "${s.unknownOrientation} without orientation")
    return parts.joinToString(" · ")
}

/** Etiqueta de una piedra: orientación votada y, si se sabe, sombra o sol. */
fun AssistantPresenter.stoneLabel(s: AssistantStone): String {
    val aspect = s.aspect ?: return t("sin orientación votada", "no voted orientation")
    val a = labels.aspect(aspect)
    return when (s.sun) {
        "SHADE" -> "$a · ${t("sombra", "shade")}"
        "SUN" -> "$a · ${t("sol", "sun")}"
        "MIXED" -> "$a · ${t("sol y sombra", "sun and shade")}"
        else -> a
    }
}

