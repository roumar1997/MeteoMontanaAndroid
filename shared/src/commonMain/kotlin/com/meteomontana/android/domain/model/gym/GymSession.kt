package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.LocalDate

/** Como se apunto el entreno: a mano («He entrenado aqui hoy») o al marcar «Aqui» y aceptar la propuesta. */
enum class GymSessionSource {
    MANUAL, PRESENCE;

    companion object {
        fun parse(raw: String?): GymSessionSource = entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}

/** Un dia entrenado en un rocodromo. Como mucho una por rocodromo y dia (marcar dos veces no duplica). */
data class GymSession(
    val gymId: Long,
    val date: LocalDate,
    val source: GymSessionSource = GymSessionSource.MANUAL,
    /** Id del servidor; 0 mientras la sesion solo existe en el movil. */
    val id: Long = 0,
    /** Lo hecho ese dia por color o grado (vacio si no se apunto nada). */
    val ascents: List<GymAscent> = emptyList(),
    /** Bloques de board con nombre (opcional; los recuentos de [ascents] mandan). */
    val boardProblems: List<BoardProblem> = emptyList()
) {
    val totalAscents: Int get() = ascents.sumOf { it.count }
}

/**
 * Resumen de entrenos para la tarjeta «Rocodromo» del perfil. [streakWeeks] cuenta semanas seguidas (lunes a
 * domingo) con al menos un entreno; la semana en curso no corta la racha si todavia no has entrenado en ella.
 */
data class GymStats(
    val week: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
    val topGymId: Long? = null,
    val topGymVisits: Int = 0,
    val streakWeeks: Int = 0
) {
    val isEmpty: Boolean get() = year == 0 && streakWeeks == 0
}

/** Calculo puro de las estadisticas (igual en iOS y Android). Las fechas son las del movil. */
object GymStatsCalculator {

    fun compute(sessions: List<GymSession>, today: LocalDate): GymStats {
        if (sessions.isEmpty()) return GymStats()
        val unique = sessions.distinctBy { it.gymId to it.date }
        val thisWeek = weekIndex(today)
        val ofYear = unique.filter { it.date.year == today.year }
        val top = ofYear.groupBy { it.gymId }
            .map { (id, list) -> Triple(id, list.size, list.maxOf { it.date }) }
            .maxWithOrNull(compareBy<Triple<Long, Int, LocalDate>> { it.second }.thenBy { it.third }.thenByDescending { it.first })
        return GymStats(
            week = unique.count { weekIndex(it.date) == thisWeek },
            month = unique.count { it.date.year == today.year && it.date.monthNumber == today.monthNumber },
            year = ofYear.size,
            topGymId = top?.first,
            topGymVisits = top?.second ?: 0,
            streakWeeks = streakWeeks(unique, thisWeek)
        )
    }

    private fun streakWeeks(sessions: List<GymSession>, thisWeek: Int): Int {
        val weeks = sessions.map { weekIndex(it.date) }.toSet()
        var week = if (thisWeek in weeks) thisWeek else thisWeek - 1
        var streak = 0
        while (week in weeks) { streak++; week-- }
        return streak
    }

    /** Numero de semana (lunes primero) contado desde 1970: 1970-01-05 abre la semana 1. */
    internal fun weekIndex(date: LocalDate): Int = (date.toEpochDays() + 3).floorDiv(7)
}
