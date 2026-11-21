package com.meteomontana.android.domain.model.gym

/**
 * Una ascension (o varias iguales) de la lista que sale al filtrar por grado. [date] es «2026-11-18», o null para
 * «antes»: lo que el resumen cuenta pero ya no trae con su dia (el servidor solo manda los ultimos dias de cada sitio).
 * No se guarda el nombre de la via o del bloque: solo grado, sitio y dia.
 */
data class AscentEntry(val gymId: Long, val gymName: String?, val date: String?, val key: String, val count: Int)

/** Filtro por grado de Ascensiones: que grados se pueden elegir y que ascensiones sueltas salen con los elegidos. */
object AscentEntries {
    /** Los grados que hay en [items] (colores aparte, no tienen grado), de facil a dificil. */
    fun grades(items: List<GymAscent>): List<String> = GymBreakdown.byGrade(items).map { it.first }

    /**
     * Las ascensiones de [summary] que entran en [filter] y cuyo grado esta en [grades] (minusculas), una por sitio, dia y
     * clave: las de mas reciente a mas antigua y, al final, lo que no trae dia («antes»).
     */
    fun entries(summary: GymSummary, filter: AscentFilter, grades: Set<String>): List<AscentEntry> {
        if (grades.isEmpty()) return emptyList()
        val out = mutableListOf<AscentEntry>()
        for (g in summary.byGym) {
            val total = GymBreakdown.filter(g.items, filter).filter { GymAscentKeys.gradeOfKey(it.key) in grades }
            val dated = LinkedHashMap<String, Int>()
            for (d in g.days) for (a in GymBreakdown.filter(d.items, filter)) {
                if (GymAscentKeys.gradeOfKey(a.key) !in grades) continue
                out += AscentEntry(g.gymId, g.name, d.date, a.key, a.count)
                dated[a.key] = (dated[a.key] ?: 0) + a.count
            }
            for (t in total) {
                val left = t.count - (dated[t.key] ?: 0)
                if (left > 0) out += AscentEntry(g.gymId, g.name, null, t.key, left)
            }
        }
        return out.sortedWith(compareByDescending<AscentEntry> { it.date ?: "" }.thenBy { GymAscentKeys.gradeRank(GymAscentKeys.gradeOfKey(it.key).orEmpty()) * -1 })
    }
}
