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
    val homeGym: HomeGym? = null,
    /** Orden de las categorias de sus Ascensiones («BOARDS,BLOCKS,ROUTES»); null si no lo cambio (vale [AscentTabOrder.default]). */
    val ascentOrder: String? = null,
    /** Orden de sus rocodromos (ids «5,3,9»); null si no lo cambio (vale [GymOrder]: favoritos y luego por ascensiones). */
    val gymOrder: String? = null,
    /** Bloques de board con nombre de todo el historial (mas recientes primero; para buscar por nombre). */
    val boardProblems: List<NamedBoardProblem> = emptyList()
) {
    val isEmpty: Boolean get() = sessions == 0
}

/** Rocódromo habitual de una persona, con lo justo para pintarlo y abrir su ficha. */
data class HomeGym(val id: Long, val name: String, val city: String? = null)

/**
 * Lo de una persona en UN rocodromo: sus claves ([items], cada rocodromo con su escala: colores o grados) y sus dias
 * ([days], el mas reciente primero). Servidores antiguos no los mandan: quedan vacios.
 */
data class GymSummaryGym(
    val gymId: Long, val name: String?, val sessions: Int, val ascents: Int, val maxGrade: String?,
    val items: List<GymAscent> = emptyList(),
    val days: List<GymSummaryDay> = emptyList()
)

/** Un dia en un rocodromo: «2026-11-18», cuantas ascensiones y cuales ([items]; vacio si el servidor es antiguo). */
data class GymSummaryDay(val date: String, val ascents: Int, val items: List<GymAscent> = emptyList(), val boardProblems: List<BoardProblem> = emptyList())

@Serializable
data class GymSummaryMonth(val month: String, val ascents: Int)

/** Qué ascensiones se miran en la pantalla de detalle: todas o solo bloques, vias o boards. */
enum class AscentFilter { ALL, BLOCKS, ROUTES, BOARDS }

/** Calculo puro del resumen y sus desgloses. Se usa con las sesiones del movil y para filtrar lo que manda el servidor. */
object GymBreakdown {

    /** Resumen calculado con las sesiones locales (sin red). [name] da el nombre de cada rocodromo si se conoce. */
    fun summarize(sessions: List<GymSession>, today: LocalDate, name: (Long) -> String? = { null }): GymSummary {
        val perGym = LinkedHashMap<Long, IntArray>()
        val keysByGym = HashMap<Long, LinkedHashMap<String, Int>>()
        val daysByGym = HashMap<Long, MutableList<GymSummaryDay>>()
        val maxByGym = HashMap<Long, String?>()
        val byKey = LinkedHashMap<String, Int>()
        val named = mutableListOf<NamedBoardProblem>()
        val byMonth = HashMap<String, Int>()
        var max: String? = null
        var total = 0
        for (s in sessions) {
            val row = perGym.getOrPut(s.gymId) { IntArray(2) }
            row[0]++
            row[1] += s.totalAscents
            total += s.totalAscents
            daysByGym.getOrPut(s.gymId) { mutableListOf() }.add(GymSummaryDay(s.date.toString(), s.totalAscents, s.ascents.map { GymAscent(it.key, it.count) }, s.boardProblems))
            s.boardProblems.forEach { named += NamedBoardProblem(s.gymId, s.date.toString(), it) }
            val monthKey = monthKey(s.date)
            byMonth[monthKey] = (byMonth[monthKey] ?: 0) + s.totalAscents
            for (a in s.ascents) {
                byKey[a.key] = (byKey[a.key] ?: 0) + a.count
                val gymKeys = keysByGym.getOrPut(s.gymId) { LinkedHashMap() }
                gymKeys[a.key] = (gymKeys[a.key] ?: 0) + a.count
                val g = GymAscentKeys.maxGradeOfKey(a.key)
                max = GymAscentKeys.harder(max, g)
                maxByGym[s.gymId] = GymAscentKeys.harder(maxByGym[s.gymId], g)
            }
        }
        val byGym = perGym.map { (id, r) -> GymSummaryGym(id, name(id), r[0], r[1], maxByGym[id], sortedItems(keysByGym[id]), daysByGym[id].orEmpty().sortedByDescending { it.date }.take(MAX_DAYS)) }
            .sortedWith(compareByDescending<GymSummaryGym> { it.ascents }.thenByDescending { it.sessions })
        val items = byKey.map { GymAscent(it.key, it.value) }.sortedByDescending { it.count }
        val months = (11 downTo 0).map { back ->
            val key = monthKey(shiftMonths(today, -back))
            GymSummaryMonth(key, byMonth[key] ?: 0)
        }
        return GymSummary(sessions.size, perGym.size, total, max, byGym, items, months, boardProblems = named.sortedByDescending { it.date })
    }

    private const val MAX_DAYS = 60

    private fun sortedItems(m: Map<String, Int>?): List<GymAscent> =
        m.orEmpty().map { GymAscent(it.key, it.value) }.sortedByDescending { it.count }

    /** Los rocodromos de [summary] con SOLO lo que entra en [filter] (los que se quedan sin nada, fuera); cada uno con su propia escala. */
    fun gymsFiltered(summary: GymSummary, filter: AscentFilter): List<GymSummaryGym> =
        summary.byGym.mapNotNull { g ->
            val items = filter(g.items, filter)
            if (items.isEmpty()) null else g.copy(items = items, ascents = items.sumOf { it.count })
        }

    /** ¿El servidor mando el desglose por rocodromo? (si no, solo hay el total: se pinta como antes). */
    fun hasGymBreakdown(summary: GymSummary): Boolean = summary.byGym.any { it.items.isNotEmpty() }

    /** Boards agrupados por tablero (Kilter, MoonBoard...), el que mas se uso primero; dentro, lo de cada grado. */
    fun boardsByKind(items: List<GymAscent>): List<Pair<BoardKind, List<GymAscent>>> =
        BoardKind.entries.mapNotNull { kind -> GymAscentKeys.boards(items, kind).takeIf { it.isNotEmpty() }?.let { kind to it } }
            .sortedByDescending { (_, list) -> list.sumOf { it.count } }

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
