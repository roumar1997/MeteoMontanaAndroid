package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.util.GradeRange
import com.meteomontana.android.domain.util.gradeScore

/**
 * Responde a las preguntas del asistente sobre los datos PROPIOS del usuario (su diario, sus favoritas,
 * sus quedadas) sin que salgan del móvil: el servidor solo entiende la frase, y estas cuentas se hacen
 * aquí con lo que la app ya tiene guardado. Puro Kotlin, sin red ni reloj ("hoy" entra por parámetro):
 * Android e iOS comparten exactamente las mismas reglas y se prueba en JVM.
 *
 * Devuelve cifras y nombres, nunca frases: el texto (ES/EN) lo escribe cada app.
 */
object MineAnswerer {

    /**
     * @param topic STATS | FAVORITES | LAST_VISIT | MEETUPS
     * @param count nº de entradas / favoritas / quedadas que cumplen lo pedido
     * @param maxGrade grado más duro entre las entradas (solo STATS)
     * @param lastDate última fecha ("yyyy-MM-dd") (solo LAST_VISIT)
     * @param place nombre de la escuela por la que se filtró, si la hubo
     * @param items nombres para listar (favoritas o quedadas)
     */
    data class MineAnswer(
        val topic: String,
        val count: Int = 0,
        val maxGrade: String? = null,
        val lastDate: String? = null,
        val place: String? = null,
        val items: List<String> = emptyList()
    )

    /** Máximo de nombres que se listan en una respuesta. */
    const val MAX_ITEMS = 8

    /** Cuántas encadenadas (hechas, no proyectos) cumplen los filtros, y el grado más duro entre ellas. */
    fun stats(
        journal: List<JournalSession>,
        discipline: String?, gradeMin: String?, gradeMax: String?, year: Int?,
        schoolId: String?, schoolName: String?
    ): MineAnswer {
        val minScore = gradeScore(gradeMin)
        val maxScore = gradeScore(gradeMax)
        val done = journal.asSequence()
            .filter { it.status != "PROJECT" }
            .filter { discipline == null || it.discipline == discipline }
            .filter { year == null || it.date.startsWith(year.toString()) }
            .filter { schoolId == null && schoolName == null || fromSchool(it, schoolId, schoolName) }
            .filter { e ->
                if (minScore == null && maxScore == null) return@filter true
                val s = gradeScore(GradeRange.base(e.grade)) ?: return@filter false
                (minScore == null || s >= minScore) && (maxScore == null || s <= maxScore)
            }
            .toList()
        val hardest = done.mapNotNull { e -> gradeScore(GradeRange.base(e.grade))?.let { it to e.grade } }
            .maxByOrNull { it.first }?.second
        return MineAnswer("STATS", count = done.size, maxGrade = GradeRange.base(hardest), place = schoolName)
    }

    /** Última vez que escaló en una escuela (o en cualquiera si no se dice cuál). */
    fun lastVisit(journal: List<JournalSession>, schoolId: String?, schoolName: String?): MineAnswer {
        val last = journal.filter { it.status != "PROJECT" }
            .filter { schoolId == null && schoolName == null || fromSchool(it, schoolId, schoolName) }
            .maxOfOrNull { it.date }
        return MineAnswer("LAST_VISIT", count = if (last == null) 0 else 1, lastDate = last, place = schoolName)
    }

    fun favorites(favs: List<FavoriteSchool>): MineAnswer =
        MineAnswer("FAVORITES", count = favs.size, items = favs.map { it.name }.take(MAX_ITEMS))

    /** Quedadas que aún no han pasado (su último día es hoy o después), las más próximas primero. */
    fun meetups(all: List<Meetup>, today: String, schoolId: String?, joinedOnly: Boolean = false): MineAnswer {
        val upcoming = all.filter { it.lastDay >= today }
            .filter { schoolId == null || it.schoolId == schoolId }
            .filter { !joinedOnly || it.joined }
            .sortedBy { it.days.firstOrNull() ?: it.lastDay }
        return MineAnswer(
            "MEETUPS", count = upcoming.size,
            items = upcoming.take(MAX_ITEMS).map { m ->
                val where = m.schoolName?.let { " · $it" } ?: ""
                "${m.name}$where · ${m.days.firstOrNull() ?: m.lastDay}"
            }
        )
    }

    /**
     * "Bloques de 7a que no haya hecho": quita las vías que ya tiene en el diario como hechas. Se reconoce
     * por el id de la vía, o por el nombre en las entradas antiguas que no lo guardaron. Las piedras (sin
     * vía) se dejan: una piedra no se "hace" entera.
     */
    fun excludeDone(hits: List<LineSearchHit>, journal: List<JournalSession>): List<LineSearchHit> {
        val done = journal.filter { it.status != "PROJECT" }
        val ids = done.mapNotNull { it.lineId }.toSet()
        val names = done.filter { it.lineId == null }.map { it.blockName.trim().lowercase() }.toSet()
        return hits.filter { h ->
            val id = h.lineId ?: return@filter true
            id !in ids && (h.lineName?.trim()?.lowercase() ?: "") !in names
        }
    }

    private fun fromSchool(e: JournalSession, schoolId: String?, schoolName: String?): Boolean =
        (schoolId != null && e.schoolId == schoolId) ||
            (schoolName != null && e.schoolName.equals(schoolName, ignoreCase = true))
}
