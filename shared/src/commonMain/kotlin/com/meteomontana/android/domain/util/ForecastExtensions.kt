package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.HourForecast
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Devuelve las próximas N horas a partir de la hora local actual.
 * Si no hay datos a partir de ahora (forecast en el pasado), devuelve las primeras N.
 *
 * KMP-friendly: usa kotlinx.datetime, vive en commonMain.
 */
fun Forecast.hoursFromNow(count: Int = 16): List<HourForecast> =
    hours.fromNow(count)

fun List<HourForecast>.fromNow(count: Int = 16): List<HourForecast> {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val cmp = "%04d-%02d-%02dT%02d".format(now.year, now.monthNumber, now.dayOfMonth, now.hour)
    val fromNow = dropWhile { it.time.substring(0, minOf(13, it.time.length)) < cmp }
    return if (fromNow.isEmpty()) take(count) else fromNow.take(count)
}

/**
 * Helper portable de String.format("%04d", ...) — Kotlin Multiplatform no tiene String.format,
 * así que reimplementamos lo mínimo necesario.
 */
private fun String.format(vararg args: Any?): String {
    var out = this
    val regex = Regex("%(0(\\d+))?d")
    var i = 0
    out = regex.replace(out) {
        val pad = it.groupValues[2].toIntOrNull() ?: 0
        val v = (args[i++] as Number).toLong().toString()
        if (pad > 0) v.padStart(pad, '0') else v
    }
    return out
}
