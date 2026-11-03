package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.util.gradeScore

/** Orden de la lista de vías de una escuela (pestaña BLOQUES/VÍAS de la ficha). */
enum class LinesOrder { MOST_REPEATED, BEST_RATED }

/** Alturas con las que se puede filtrar la beta; el valor es el que entiende el servidor (`beta=`). */
object BetaHeights {
    // `val` y no `const val`: así Swift los lee igual que el resto (`BetaHeights.shared.TALL`).
    val TALL = "TALL"     // +1,70 m
    val SHORT = "SHORT"   // -1,70 m
    val ALL: List<String> = listOf(TALL, SHORT)
}

/**
 * Filtros de la lista. Todo opcional: texto vacío, sin grados y sin alturas = sin filtrar.
 * `grades` va en MAYÚSCULAS ("6A+"). Con varias alturas se ven las vías que tengan CUALQUIERA (unión).
 */
data class LinesFilter(
    val query: String,
    val grades: Set<String>,
    val betaHeights: Set<String>,
    val order: LinesOrder,
    /** Caras marcadas ("N", "E", "S", "O"). Vacío = sin filtrar. "N" casa con N, NE y NO. */
    val faces: Set<String> = emptySet(),
    /** Vías que NO deben salir (las que ya tienes hechas, para "Sin hacer"). Lo calcula la app con su diario. */
    val excludeLineIds: Set<String> = emptySet()
)

/**
 * Resultado de aplicar los filtros. Con filtro de cara, las piedras que nadie ha orientado todavía no se pueden
 * descartar ni dar por buenas: van aparte en [unknownFace] para que la UI las enseñe al final.
 */
data class FilteredLines(val matched: List<LineSearchHit>, val unknownFace: List<LineSearchHit>)

/** Vías de un mismo sector. `sector == null` = sin sector (la UI pone el texto traducido). */
data class SectorGroup(val sector: String?, val hits: List<LineSearchHit>)

/** Lo que pinta la pantalla: total, podio (los más repetidos) y el resto por sectores. */
data class LinesView(val total: Int, val podium: List<LineSearchHit>, val groups: List<SectorGroup>)

/**
 * Lógica PURA de la pestaña BLOQUES/VÍAS de una escuela: filtrar, ordenar, podio y agrupar por sector.
 * Vive en shared para que iOS y Android hagan EXACTAMENTE lo mismo (y se pruebe una sola vez).
 * Sin red ni plataforma: recibe las vías ya descargadas ([LoadSchoolLinesUseCase]) y los ids con beta
 * ([LoadBetaLineIdsUseCase]).
 */
object SchoolLinesExplorer {

    private const val PODIUM_SIZE = 3

    /** Grados que de verdad hay en estas vías, del más fácil al más difícil (los no puntuables, al final). */
    fun gradeOptions(lines: List<LineSearchHit>): List<String> =
        lines.filter { it.lineId != null }
            .mapNotNull { it.grade?.trim()?.uppercase()?.takeIf { g -> g.isNotEmpty() } }
            .distinct()
            .sortedWith(compareBy({ gradeScore(it) ?: Int.MAX_VALUE }, { it }))

    /**
     * Aplica filtros y orden. Devuelve `null` si falta el conjunto de ids de alguna altura de beta marcada
     * (todavía se está descargando): la pantalla debe enseñar "cargando", no una lista falsa.
     */
    fun view(
        lines: List<LineSearchHit>,
        filter: LinesFilter,
        betaLineIds: Map<String, Set<String>>
    ): LinesView? {
        val hits = filtered(lines, filter, betaLineIds)?.matched ?: return null
        val sorted = hits.sortedWith(comparator(filter.order))
        val podium = if (filter.order == LinesOrder.MOST_REPEATED) {
            sorted.take(PODIUM_SIZE).filter { repetitions(it) > 0 }
        } else emptyList()
        return LinesView(sorted.size, podium, sectorGroups(sorted.drop(podium.size)))
    }

    /** Las 4 caras que se pueden marcar, en el orden en que se enseñan. */
    val FACES: List<String> = listOf("N", "E", "S", "O")

    /**
     * Aplica TODOS los filtros (los comparten la lista y la guía). `null` si falta el conjunto de ids de alguna
     * altura de beta marcada (se está descargando).
     */
    fun filtered(
        lines: List<LineSearchHit>,
        filter: LinesFilter,
        betaLineIds: Map<String, Set<String>>
    ): FilteredLines? {
        var hits = lines.filter { it.lineId != null && it.lineId !in filter.excludeLineIds }

        val q = fold(filter.query)
        if (q.isNotEmpty()) {
            hits = hits.filter { fold("${it.lineName.orEmpty()} ${it.blockName} ${it.sectorName.orEmpty()}").contains(q) }
        }
        if (filter.grades.isNotEmpty()) {
            hits = hits.filter { (it.grade ?: "").uppercase() in filter.grades }
        }
        if (filter.betaHeights.isNotEmpty()) {
            val ids = HashSet<String>()
            for (height in filter.betaHeights) ids += betaLineIds[height] ?: return null
            hits = hits.filter { it.lineId in ids }
        }
        if (filter.faces.isEmpty()) return FilteredLines(hits, emptyList())
        val (known, unknown) = hits.partition { !it.orientation.isNullOrBlank() }
        return FilteredLines(known.filter { faceMatches(it.orientation!!, filter.faces) }, unknown)
    }

    /** "NE" es cara N y cara E; "NO" es N y O. Acepta también "W"/"NW" por si llegan en inglés. */
    fun faceMatches(orientation: String, faces: Set<String>): Boolean {
        val o = orientation.trim().uppercase().replace('W', 'O')
        return faces.any { f -> o.contains(f) }
    }

    /** Agrupa por sector conservando el orden recibido; los grupos salen por su mejor vía. */
    fun sectorGroups(hits: List<LineSearchHit>): List<SectorGroup> {
        val order = LinkedHashMap<String?, MutableList<LineSearchHit>>()
        for (h in hits) order.getOrPut(h.sectorName?.takeIf { it.isNotBlank() }) { mutableListOf() }.add(h)
        return order.map { (sector, list) -> SectorGroup(sector, list) }
    }

    /** Sin mayúsculas, sin tildes y sin espacios en los extremos: "fuente" encuentra "La Fuente". */
    fun fold(text: String): String = buildString {
        for (c in text.trim().lowercase()) append(foldChar(c))
    }

    private fun foldChar(c: Char): Char = when (c) {
        'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ñ' -> 'n'
        'ç' -> 'c'
        else -> c
    }

    private fun repetitions(h: LineSearchHit): Int = h.repetitions ?: 0

    /** Media de estrellas, solo si alguien la ha valorado. */
    private fun stars(h: LineSearchHit): Double =
        if ((h.ratingCount ?: 0) > 0) h.rating ?: 0.0 else 0.0

    /** Orden total y estable: los empates se resuelven por nombre y, al final, por id. */
    private fun comparator(order: LinesOrder) = Comparator<LineSearchHit> { a, b ->
        val reps = repetitions(b).compareTo(repetitions(a))
        val stars = stars(b).compareTo(stars(a))
        val first = if (order == LinesOrder.MOST_REPEATED) reps else stars
        val second = if (order == LinesOrder.MOST_REPEATED) stars else reps
        when {
            first != 0 -> first
            second != 0 -> second
            else -> {
                val byName = (a.lineName ?: "").compareTo(b.lineName ?: "")
                if (byName != 0) byName else (a.lineId ?: "").compareTo(b.lineId ?: "")
            }
        }
    }
}
