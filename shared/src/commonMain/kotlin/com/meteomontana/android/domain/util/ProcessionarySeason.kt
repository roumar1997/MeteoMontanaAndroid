package com.meteomontana.android.domain.util

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Ventana típica de la procesionaria del pino: peligrosa (sobre todo para
 * perros, por contacto con los pelos urticantes) desde el otoño-invierno
 * hasta bien entrada la primavera. Fecha fija a propósito — no hay ninguna
 * fuente de datos fiable por zona, espejo exacto de ProcessionarySeason.java
 * del backend (misma ventana dic-may). Solo decide el estado VISUAL del
 * icono (alarmado o no); confirmar un avistamiento no depende de esto.
 */
object ProcessionarySeason {
    private val SEASON_MONTHS = setOf(12, 1, 2, 3, 4, 5)

    fun isInSeason(): Boolean {
        val month = Clock.System.todayIn(TimeZone.currentSystemDefault()).monthNumber
        return month in SEASON_MONTHS
    }
}
