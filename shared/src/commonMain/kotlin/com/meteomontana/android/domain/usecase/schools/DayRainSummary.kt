package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.RangeScore

/**
 * Lluvia de UN día resumida sobre las escuelas de la lista: mediana de milímetros y cuántas escuelas llueven.
 * `rainy` = llueve en la MITAD o más de las escuelas (mismo criterio por escuela que el servidor: ≥1 mm o ≥60 %).
 */
data class DayRain(val date: String, val medianMm: Double, val rainySchools: Int, val schools: Int) {
    val rainy: Boolean get() = schools > 0 && rainySchools * 2 >= schools
}

/** Resumen por día para los botones del selector de días de la lista de Escuelas. Puro (iOS y Android). */
object DayRainSummary {

    fun summarize(ranges: List<RangeScore>, dates: List<String>): List<DayRain> =
        dates.map { date ->
            val days = ranges.mapNotNull { r -> r.days.firstOrNull { it.date == date } }
            if (days.isEmpty()) DayRain(date, 0.0, 0, 0)
            else DayRain(date, median(days.map { it.rainMm }), days.count { it.rainy }, days.size)
        }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }
}
