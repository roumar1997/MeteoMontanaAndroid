package com.meteomontana.android.presentation.assistant

import com.meteomontana.android.presentation.assistant.AssistantPresenter.HitGroup
import com.meteomontana.android.presentation.assistant.AssistantPresenter.HitSector
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantClarification
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantRestriction
import com.meteomontana.android.domain.model.AssistantRestrictions
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.usecase.journal.Achievement
import com.meteomontana.android.domain.usecase.journal.GoalProgress
import com.meteomontana.android.domain.model.AssistantSector
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantStone
import com.meteomontana.android.domain.model.AssistantSummary
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.assistant.MineAnswerer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Textos del asistente sobre resultados de búsqueda: agrupar por escuela y sector, estrellas y comparar (extensiones de AssistantPresenter).


/**
 * TODOS los resultados, agrupados por escuela y sector, sin recortar nada: lo que no cabe en pantalla se resuelve
 * plegando y desplegando, no escondiendo filas (Álvaro, 2026-10-08: "me tiene que salir la lista de todas").
 */
fun AssistantPresenter.groupAllHits(hits: List<LineSearchHit>): List<HitGroup> =
    groupHits(hits, maxSchools = Int.MAX_VALUE, perSchool = Int.MAX_VALUE, perSchoolWhenAlone = Int.MAX_VALUE)

/**
 * Qué escuelas empiezan desplegadas: con pocos resultados todas; con muchos solo la primera, para que el chat no
 * se haga eterno ni se carguen cientos de miniaturas que nadie está viendo.
 */
fun AssistantPresenter.initiallyExpanded(groups: List<HitGroup>, limit: Int = 20): Set<String> {
    val total = groups.sumOf { it.total }
    return if (total <= limit) groups.map { it.id }.toSet() else groups.take(1).map { it.id }.toSet()
}

/** Clave estable de un sector dentro de una escuela (para saber si está plegado). */
fun AssistantPresenter.sectorKey(schoolId: String, sectorId: String) = "$schoolId|$sectorId"

/**
 * Agrupa los resultados por escuela conservando el orden en que llegan (el servidor los manda por cercanía) y,
 * dentro de cada escuela, por sector. Como mucho [maxSchools] escuelas; con varias se enseñan [perSchool] filas de
 * cada una, y si solo hay UNA se enseñan hasta [perSchoolWhenAlone].
 */
fun AssistantPresenter.groupHits(hits: List<LineSearchHit>, maxSchools: Int = 5, perSchool: Int = 4, perSchoolWhenAlone: Int = 60): List<HitGroup> {
    val order = mutableListOf<String>()
    val bySchool = LinkedHashMap<String, MutableList<LineSearchHit>>()
    for (h in hits) {
        if (h.schoolId !in bySchool) order += h.schoolId
        bySchool.getOrPut(h.schoolId) { mutableListOf() }.add(h)
    }
    val chosen = order.take(maxSchools)
    val limit = if (chosen.size == 1) perSchoolWhenAlone else perSchool
    return chosen.map { id ->
        val all = bySchool[id].orEmpty()
        HitGroup(id, all.firstOrNull()?.schoolName.orEmpty(), bySector(all.take(limit)), all.size)
    }
}

/** Reparte por sector: primero los que más resultados tienen; "sin sector" siempre al final. */
fun AssistantPresenter.bySector(hits: List<LineSearchHit>): List<HitSector> {
    val bySector = LinkedHashMap<String, MutableList<LineSearchHit>>()
    for (h in hits) bySector.getOrPut((h.sectorName ?: "").trim()) { mutableListOf() }.add(h)
    return bySector.map { (name, list) -> HitSector(name.ifEmpty { null }, list) }
        .sortedWith(Comparator { a, b ->
            when {
                (a.name == null) != (b.name == null) -> if (b.name == null) -1 else 1
                a.hits.size != b.hits.size -> b.hits.size.compareTo(a.hits.size)
                else -> (a.name ?: "").compareTo(b.name ?: "")
            }
        })
}

fun AssistantPresenter.hitsIntro(total: Int, needsLocationForDistance: Boolean, rated: Boolean = false): String {
    var text = if (rated) {
        if (total == 0) t("Todavía nadie ha valorado vías con esos filtros.", "Nobody has rated routes with those filters yet.")
        else t("Las mejor valoradas ($total):", "Top rated ($total):")
    } else {
        if (total == 0) t("No he encontrado vías o bloques con esos filtros.", "I found no routes or boulders with those filters.")
        else t("He encontrado $total resultados:", "I found $total results:")
    }
    if (needsLocationForDistance) text += " " + t("Para filtrar por distancia activa tu ubicación.", "Turn on your location to filter by distance.")
    return text
}

/** Aviso de que hay más resultados de los que caben en el chat; null si se ven todos. */
fun AssistantPresenter.moreNote(total: Int, shown: Int): String? =
    if (total > shown) t("Y ${total - shown} más. Mira todos en Escuelas → Vías/Bloques.",
        "And ${total - shown} more. See them all in Crags → Routes/Boulders.") else null

// ── Estrellas ─────────────────────────────────────────────────────────────────────────────────────────────────

/** "★ 4,5 (8)": media y número de votos, con la coma o el punto del idioma de la app. */
fun AssistantPresenter.starsLabel(rating: Double, count: Int) = "★ ${oneDecimal(rating)} ($count)"

/**
 * Deja solo las vías que alguien ha valorado (y, si dijo cuántas estrellas, con esa media o más), de más a menos
 * estrellas; a igualdad, primero las que más gente ha valorado y, si aún empatan, el orden en que llegaron.
 */
fun AssistantPresenter.rankByRating(hits: List<LineSearchHit>, minStars: Int?): List<LineSearchHit> {
    data class Rated(val hit: LineSearchHit, val avg: Double, val votes: Int, val order: Int)
    val rated = hits.mapIndexedNotNull { index, h ->
        val avg = h.rating ?: return@mapIndexedNotNull null
        val votes = h.ratingCount ?: return@mapIndexedNotNull null
        if (votes <= 0) null else Rated(h, avg, votes, index)
    }
    return rated.filter { minStars == null || it.avg >= minStars }
        .sortedWith(compareByDescending<Rated> { it.avg }.thenByDescending { it.votes }.thenBy { it.order })
        .map { it.hit }
}

// ── Comparar ──────────────────────────────────────────────────────────────────────────────────────────────────

fun AssistantPresenter.compareIntro(r: AssistantRecommendation): String {
    if (r.schools.isEmpty()) return t("No he podido comparar esas escuelas.", "I couldn't compare those crags.")
    val names = r.schools.joinToString(", ") { it.name }
    var text = t("Comparación de $names:", "Comparing $names:")
    if (r.forecastAvailable) {
        // Sin grado pedido ("compárame el tiempo en…") el nº de vías no es "de un grado": no se menciona.
        text += " " + if (r.filtered) t("Ordenadas por el tiempo de esos días y el nº de vías de ese grado.",
            "Ranked by the weather on those days and the number of routes of that grade.")
        else t("Ordenadas por el tiempo de esos días.", "Ranked by the weather on those days.")
    }
    return text
}

