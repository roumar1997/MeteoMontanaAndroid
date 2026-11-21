package com.meteomontana.android.domain.model.gym

/**
 * Una ascension (o varias iguales) de la lista que sale al filtrar por grado. [date] es «2026-11-18», o null para
 * «antes»: lo que el resumen cuenta pero ya no trae con su dia (el servidor solo manda los ultimos dias de cada sitio).
 * Los boards pueden llevar el nombre del bloque ([names]); vias y bloques no: solo grado, sitio y dia.
 */
data class AscentEntry(val gymId: Long, val gymName: String?, val date: String?, val key: String, val count: Int, val names: List<String> = emptyList())

/** Filtro por grado de Ascensiones: que grados se pueden elegir y que ascensiones sueltas salen con los elegidos. */
object AscentEntries {
    /** Los grados que hay en [items] (colores aparte, no tienen grado), de facil a dificil. */
    fun grades(items: List<GymAscent>): List<String> = GymBreakdown.byGrade(items).map { it.first }

    /**
     * Las ascensiones de [summary] que entran en [filter] y cuyo grado esta en [grades] (minusculas), una por sitio, dia y
     * clave: las de mas reciente a mas antigua y, al final, lo que no trae dia («antes»).
     */
    fun entries(summary: GymSummary, filter: AscentFilter, grades: Set<String>, includeBoards: Boolean = true): List<AscentEntry> {
        if (grades.isEmpty()) return emptyList()
        // «Todo» sin boards: los boards no se reparten por rocodromo (van en su pantalla).
        val pick = { items: List<GymAscent> -> if (filter == AscentFilter.ALL && !includeBoards) GymBreakdown.withoutBoards(items) else GymBreakdown.filter(items, filter) }
        val out = mutableListOf<AscentEntry>()
        for (g in summary.byGym) {
            val total = pick(g.items).filter { GymAscentKeys.gradeOfKey(it.key) in grades }
            val dated = LinkedHashMap<String, Int>()
            for (d in g.days) {
              val names = BoardProblems.namesByKey(d.items, d.boardProblems)
              for (a in pick(d.items)) {
                if (GymAscentKeys.gradeOfKey(a.key) !in grades) continue
                out += AscentEntry(g.gymId, g.name, d.date, a.key, a.count, names[a.key].orEmpty())
                dated[a.key] = (dated[a.key] ?: 0) + a.count
              }
            }
            for (t in total) {
                val left = t.count - (dated[t.key] ?: 0)
                if (left > 0) out += AscentEntry(g.gymId, g.name, null, t.key, left)
            }
        }
        return out.sortedWith(compareByDescending<AscentEntry> { it.date ?: "" }.thenBy { GymAscentKeys.gradeRank(GymAscentKeys.gradeOfKey(it.key).orEmpty()) * -1 })
    }
}
