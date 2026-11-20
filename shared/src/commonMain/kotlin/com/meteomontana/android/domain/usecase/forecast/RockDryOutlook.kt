package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayForecast
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Estado de la roca un día: mojada (azul), dudosa (ámbar) o seca (verde). */
enum class RockDayState { WET, DUBIOUS, DRY }

/**
 * Cuándo estará seca una escuela mojada, o hasta cuándo seguirá seca. Se calcula SOLO con la previsión que ya existe
 * (`Current.drying`, `Current.dryRock` y la lluvia diaria); sin más umbrales que dos: un día con 1 mm o más cuenta
 * como mojado y entre 0,2 y 1 mm como dudoso. Igual en iOS y Android.
 */
data class RockDryOutlook(
    val wet: Boolean,
    /** Mojada: horas hasta que esté seca (si el servidor lo estima y son 24 o menos). */
    val dryInHours: Int?,
    /** Mojada con más de 24 h: primer día seco. */
    val dryFromDate: LocalDate?,
    /** Seca: último día seco seguido (null si mañana ya empeora o no se sabe). */
    val dryUntilDate: LocalDate?,
    /** Seca: primer día que empeora (null si en el rango no llueve). */
    val worsensOnDate: LocalDate?,
    /** Franja de los próximos días (hasta 5), empezando hoy. */
    val strip: List<Pair<LocalDate, RockDayState>>
) {
    companion object {
        const val WET_MM = 1.0
        const val DUBIOUS_MM = 0.2
        const val STRIP_DAYS = 5
        const val MAX_HOURS_SHOWN = 24

        fun dayState(precipMm: Double): RockDayState = when {
            precipMm >= WET_MM -> RockDayState.WET
            precipMm >= DUBIOUS_MM -> RockDayState.DUBIOUS
            else -> RockDayState.DRY
        }

        fun of(current: Current, days: List<DayForecast>, today: LocalDate): RockDryOutlook {
            val parsed = days.mapNotNull { d ->
                runCatching { LocalDate.parse(d.date.take(10)) }.getOrNull()?.let { it to d }
            }.filter { it.first >= today }.sortedBy { it.first }
            val wet = !current.dryRock || current.drying?.wet == true
            val hours = current.drying?.dryingHours?.takeIf { it > 0 }
            val dryFrom = hours?.let { today.plus(DatePeriod(days = (it + 23) / 24)) }
            val strip = parsed.take(STRIP_DAYS).mapIndexed { i, (date, d) ->
                date to when {
                    wet && i == 0 -> RockDayState.WET
                    wet && dryFrom != null && date < dryFrom -> RockDayState.WET
                    else -> dayState(d.precipitationTotal)
                }
            }
            if (wet) {
                val inHours = hours?.takeIf { it <= MAX_HOURS_SHOWN }
                val from = if (hours != null && hours > MAX_HOURS_SHOWN) dryFrom else null
                return RockDryOutlook(true, inHours, from, null, null, strip)
            }
            val worsens = parsed.firstOrNull { it.first > today && dayState(it.second.precipitationTotal) != RockDayState.DRY }?.first
            val until = worsens?.plus(DatePeriod(days = -1))?.takeIf { it > today }
            return RockDryOutlook(false, null, null, until, worsens, strip)
        }
    }
}
