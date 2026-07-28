package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.model.JournalSession

/**
 * Cerebro de la pantalla ESTADÍSTICAS (C4) — cálculo PURO en commonMain:
 * Android e iOS pintan; los números salen de aquí, con tests. Todas las
 * funciones aceptan la lista de entradas ya filtrada por disciplina/año.
 *
 * Las fechas son "yyyy-MM-dd" (formato del diario). Sin dependencias de
 * reloj: "hoy" entra por parámetro (determinista y testeable).
 */
object JournalStatsCalculator {

    data class Summary(
        val daysOut: Int,
        val currentStreakWeeks: Int,
        val projectsFallen: Int,
        val avgPerDay: Double,
        val pyramid: List<Pair<String, Int>>,   // grado → nº, de más duro a más fácil
        val bestMonth: String?,                 // "2026-06"
        val bestMonthCount: Int
    )

    data class Progression(
        val monthlyCounts: List<Pair<String, Int>>,      // "2026-07" → nº (últimos 12)
        val maxGradePerQuarter: List<Pair<String, String>>, // "2026-Q1" → grado
        val weeksOut: List<Boolean>,                     // últimas 12 semanas, antigua→reciente
        val perSchool: List<Triple<String, Int, String?>> // escuela, nº, grado máx
    )

    /** Años presentes en el diario, descendente ("2026", "2025"...). */
    fun availableYears(entries: List<JournalSession>): List<String> =
        entries.mapNotNull { it.date.take(4).takeIf { y -> y.length == 4 } }
            .distinct().sortedDescending()

    fun filter(entries: List<JournalSession>, discipline: String?, year: String?): List<JournalSession> =
        entries.asSequence()
            .filter { it.status != "PROJECT" }
            .filter { discipline == null || it.discipline == discipline }
            .filter { year == null || it.date.startsWith(year) }
            .toList()

    fun summary(entries: List<JournalSession>, allEntries: List<JournalSession>, today: String): Summary {
        val days = entries.map { it.date }.distinct()
        val byMonth = entries.groupingBy { it.date.take(7) }.eachCount()
        val best = byMonth.maxByOrNull { it.value }
        // Proyectos caídos: la vía tiene HOY una entrada DONE y en algún momento
        // fue PROJECT (mismo lineId, o mismo nombre para entradas antiguas).
        val projectKeys = allEntries.filter { it.status == "PROJECT" }
            .map { it.lineId ?: "${it.schoolId}|${it.blockName.lowercase()}" }.toSet()
        val fallen = entries.count {
            (it.lineId ?: "${it.schoolId}|${it.blockName.lowercase()}") in projectKeys
        }
        return Summary(
            daysOut = days.size,
            currentStreakWeeks = currentStreakWeeks(days, today),
            projectsFallen = fallen,
            avgPerDay = if (days.isEmpty()) 0.0 else
                (entries.size * 10 / days.size) / 10.0,
            pyramid = pyramid(entries),
            bestMonth = best?.key,
            bestMonthCount = best?.value ?: 0
        )
    }

    fun progression(entries: List<JournalSession>, today: String): Progression {
        val months = lastMonths(today, 12)
        val byMonth = entries.groupingBy { it.date.take(7) }.eachCount()
        val quarters = entries.groupBy { quarterOf(it.date) }
            .mapValues { (_, list) -> list.mapNotNull { it.grade }.maxByOrNull(::gradeRank) }
            .filterValues { it != null }
            .toList().sortedBy { it.first }
            .map { it.first to it.second!! }
        val weeks = (11 downTo 0).map { back ->
            val weekIndex = epochWeek(today) - back
            entries.any { epochWeek(it.date) == weekIndex }
        }
        val perSchool = entries.groupBy { it.schoolName ?: it.schoolId ?: "—" }
            .map { (school, list) ->
                Triple(school, list.size, list.mapNotNull { it.grade }.maxByOrNull(::gradeRank))
            }
            .sortedByDescending { it.second }
        return Progression(
            monthlyCounts = months.map { it to (byMonth[it] ?: 0) },
            maxGradePerQuarter = quarters,
            weeksOut = weeks,
            perSchool = perSchool
        )
    }

    /** Pirámide: recuento por grado, del más duro al más fácil. */
    fun pyramid(entries: List<JournalSession>): List<Pair<String, Int>> =
        entries.mapNotNull { it.grade?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { gradeRank(it.first) }

    /**
     * Racha: semanas CONSECUTIVAS con al menos una salida, contando hacia
     * atrás desde la semana de `today` (la semana en curso cuenta si tiene
     * salida; si aún no, la racha empieza en la anterior).
     */
    fun currentStreakWeeks(days: List<String>, today: String): Int {
        if (days.isEmpty()) return 0
        val weeks = days.map(::epochWeek).toSet()
        var week = epochWeek(today)
        if (week !in weeks) week--
        var streak = 0
        while (week in weeks) { streak++; week-- }
        return streak
    }

    /**
     * Orden de grados francés: número principal + letra + plus.
     * "6c+" > "6c" > "6b+" ... Grados raros van al fondo (rank 0).
     */
    fun gradeRank(grade: String): Int {
        val g = grade.trim().lowercase()
        val m = Regex("^([3-9])([abc])?(\\+)?").find(g) ?: return 0
        val number = m.groupValues[1].toInt()
        val letter = when (m.groupValues[2]) { "a" -> 0; "b" -> 1; "c" -> 2; else -> 0 }
        val plus = if (m.groupValues[3] == "+") 1 else 0
        return number * 100 + letter * 10 + plus
    }

    // ── fechas sin dependencias (aritmética de días julianos) ───────────────

    private fun quarterOf(date: String): String {
        val month = date.substring(5, 7).toInt()
        return "${date.take(4)}-Q${(month - 1) / 3 + 1}"
    }

    private fun lastMonths(today: String, n: Int): List<String> {
        var year = today.take(4).toInt()
        var month = today.substring(5, 7).toInt()
        val out = ArrayDeque<String>()
        repeat(n) {
            out.addFirst("$year-" + month.toString().padStart(2, '0'))
            month--; if (month == 0) { month = 12; year-- }
        }
        return out.toList()
    }

    /** Nº de semana absoluta (lunes como inicio) desde la época. */
    fun epochWeek(date: String): Int = (epochDay(date) + 3) / 7  // 1970-01-01 fue jueves

    private fun epochDay(date: String): Int {
        val y = date.take(4).toInt()
        val m = date.substring(5, 7).toInt()
        val d = date.substring(8, 10).toInt()
        // Fórmula civil estándar (Howard Hinnant) — válida para todo el rango.
        val yAdj = if (m <= 2) y - 1 else y
        val era = (if (yAdj >= 0) yAdj else yAdj - 399) / 400
        val yoe = yAdj - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
