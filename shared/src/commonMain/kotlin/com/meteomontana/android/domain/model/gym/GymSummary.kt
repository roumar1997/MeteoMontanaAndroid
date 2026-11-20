package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Todo el historial de rocodromo de una persona (totales, por rocodromo, por clave y por mes). Igual en iOS y Android. */
data class GymSummary(
    val sessions: Int = 0,
    val gyms: Int = 0,
    val ascents: Int = 0,
    /** Grado frances mas alto entre vias, boards y bloques por grado (los colores no tienen grado). */
    val maxGrade: String? = null,
    val byGym: List<GymSummaryGym> = emptyList(),
    /** Lo hecho por clave (colores, grados, vias, boards), de mas a menos. */
    val items: List<GymAscent> = emptyList(),
    /** Ultimos 12 meses, del mas antiguo al actual: «2026-11» y cuantas ascensiones. */
    val byMonth: List<GymSummaryMonth> = emptyList(),
    /** Su rocódromo habitual («Tu rocódromo»), si lo ha elegido. */
    val homeGym: HomeGym? = null
) {
    val isEmpty: Boolean get() = sessions == 0
}

/** Rocódromo habitual de una persona, con lo justo para pintarlo y abrir su ficha. */
data class HomeGym(val id: Long, val name: String, val city: String? = null)

data class GymSummaryGym(val gymId: Long, val name: String?, val sessions: Int, val ascents: Int, val maxGrade: String?)

@Serializable
data class GymSummaryMonth(val month: String, val ascents: Int)

/** Qué ascensiones se miran en la pantalla de detalle: todas o solo bloques, vias o boards. */
enum class AscentFilter { ALL, BLOCKS, ROUTES, BOARDS }

/** Calculo puro del resumen y sus desgloses. Se usa con las sesiones del movil y para filtrar lo que manda el servidor. */
object GymBreakdown {

    /** Resumen calculado con las sesiones locales (sin red). [name] da el nombre de cada rocodromo si se conoce. */
    fun summarize(sessions: List<GymSession>, today: LocalDate, name: (Long) -> String? = { null }): GymSummary {
        val perGym = LinkedHashMap<Long, IntArray>()
        val maxByGym = HashMap<Long, String?>()
        val byKey = LinkedHashMap<String, Int>()
        val byMonth = HashMap<String, Int>()
        var max: String? = null
        var total = 0
        for (s in sessions) {
            val row = perGym.getOrPut(s.gymId) { IntArray(2) }
            row[0]++
            row[1] += s.totalAscents
            total += s.totalAscents
            val monthKey = monthKey(s.date)
            byMonth[monthKey] = (byMonth[monthKey] ?: 0) + s.totalAscents
            for (a in s.ascents) {
                byKey[a.key] = (byKey[a.key] ?: 0) + a.count
                val g = GymAscentKeys.maxGradeOfKey(a.key)
                max = GymAscentKeys.harder(max, g)
                maxByGym[s.gymId] = GymAscentKeys.harder(maxByGym[s.gymId], g)
            }
        }
        val byGym = perGym.map { (id, r) -> GymSummaryGym(id, name(id), r[0], r[1], maxByGym[id]) }
            .sortedWith(compareByDescending<GymSummaryGym> { it.ascents }.thenByDescending { it.sessions })
        val items = byKey.map { GymAscent(it.key, it.value) }.sortedByDescending { it.count }
        val months = (11 downTo 0).map { back ->
            val key = monthKey(shiftMonths(today, -back))
            GymSummaryMonth(key, byMonth[key] ?: 0)
        }
        return GymSummary(sessions.size, perGym.size, total, max, byGym, items, months)
    }

    /** Las claves de [items] que entran en el [filter]. */
    fun filter(items: List<GymAscent>, filter: AscentFilter): List<GymAscent> = when (filter) {
        AscentFilter.ALL -> items
        AscentFilter.BLOCKS -> GymAscentKeys.blocks(items)
        AscentFilter.ROUTES -> GymAscentKeys.routes(items)
        AscentFilter.BOARDS -> items.filter { GymAscentKeys.parse(it.key) is AscentKey.Board }
    }

    /** Suma por grado BASE (6b+/6c cuenta como 6b+) de las claves con grado; los colores se devuelven aparte por nombre. */
    fun byGrade(items: List<GymAscent>): List<Pair<String, Int>> {
        val sums = LinkedHashMap<String, Int>()
        for (a in items) {
            val g = GymAscentKeys.gradeOfKey(a.key) ?: continue
            sums[g] = (sums[g] ?: 0) + a.count
        }
        return sums.entries.sortedBy { GymAscentKeys.gradeRank(it.key) }.map { it.key to it.value }
    }

    /** Las claves que son colores de una escala (bloques sin grado), con su cantidad. */
    fun byColor(items: List<GymAscent>): List<GymAscent> =
        GymAscentKeys.blocks(items).filter { GymAscentKeys.gradeOfKey(it.key) == null }

    private fun monthKey(d: LocalDate): String = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}"

    private fun shiftMonths(d: LocalDate, delta: Int): LocalDate {
        val idx = d.year * 12 + (d.monthNumber - 1) + delta
        return LocalDate(idx / 12, idx % 12 + 1, 1)
    }
}
