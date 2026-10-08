package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.WeekendAlert

/**
 * Las reglas de las ACCIONES que el asistente propone y el usuario confirma (apuntar en el diario,
 * activar el aviso de fin de semana). Puro Kotlin, sin red: Android e iOS comparten las mismas reglas
 * y se prueban en JVM. Quien hace la llamada de red y pide la confirmación es cada app.
 */
object AssistantActions {

    /** Máximo de vías candidatas que se ofrecen cuando el nombre es ambiguo. */
    const val MAX_CANDIDATES = 3

    /** Máximo de escuelas del aviso de fin de semana (lo valida el servidor igual). */
    const val MAX_ALERT_SCHOOLS = 3

    /**
     * Las vías que pueden ser la que dijo el usuario: solo vías (no piedras), de la escuela si se sabe,
     * y si alguna se llama EXACTAMENTE así (sin mayúsculas ni tildes) solo esas; si no, las que lo contienen.
     */
    fun matchLines(hits: List<LineSearchHit>, name: String, schoolId: String?): List<LineSearchHit> {
        val wanted = fold(name)
        if (wanted.isEmpty()) return emptyList()
        val lines = hits.filter { it.lineId != null && (schoolId == null || it.schoolId == schoolId) }
        val exact = lines.filter { fold(it.lineName.orEmpty()) == wanted }
        return (if (exact.isNotEmpty()) exact else lines.filter { fold(it.lineName.orEmpty()).contains(wanted) })
            .distinctBy { it.lineId }
            .take(MAX_CANDIDATES)
    }

    /** La entrada de diario de una vía encontrada. [status] es DONE o PROJECT; [date] "yyyy-MM-dd". */
    fun journalRequest(hit: LineSearchHit, status: String, date: String): CreateJournalRequest =
        CreateJournalRequest(
            schoolId = hit.schoolId, schoolName = hit.schoolName, sector = hit.sectorName,
            blockName = hit.lineName ?: hit.blockName, grade = hit.grade, notes = null, date = date,
            discipline = hit.discipline ?: "BOULDER", lineId = hit.lineId, status = status,
            aVista = false, alFlash = false
        )

    /** ¿Ya está esa vía apuntada con ese estado? (para no duplicar la entrada). */
    fun isLogged(journal: List<JournalSession>, hit: LineSearchHit, status: String): Boolean =
        journal.any { e ->
            (e.status == status) &&
                ((hit.lineId != null && e.lineId == hit.lineId) ||
                    (e.lineId == null && e.blockName.trim().equals(hit.lineName?.trim(), ignoreCase = true)))
        }

    /** Clave con la que la app marca una vía como hecha (la misma que usa la ficha de la piedra). */
    fun doneKey(hit: LineSearchHit): String = "${hit.schoolId}|#${hit.lineId}"

    /** Resultado de añadir una escuela al aviso de fin de semana. */
    data class AlertChange(val alert: WeekendAlert?, val already: Boolean = false, val full: Boolean = false)

    /**
     * Añade [schoolId] al aviso de fin de semana y lo enciende. Si el aviso estaba en modo "cerca de mí",
     * pasa a escuelas elegidas (solo esta). Si ya la tenía, no cambia nada; si ya hay [MAX_ALERT_SCHOOLS],
     * no cabe y se dice. Se conservan día, hora y días a comparar.
     */
    fun withSchoolAlert(current: WeekendAlert, schoolId: String): AlertChange {
        val keepList = current.mode == "SCHOOLS"
        val existing = if (keepList) current.schoolIds else emptyList()
        if (current.enabled && schoolId in existing) return AlertChange(null, already = true)
        if (schoolId !in existing && existing.size >= MAX_ALERT_SCHOOLS) return AlertChange(null, full = true)
        val schools = (existing + schoolId).distinct()
        return AlertChange(current.copy(enabled = true, mode = "SCHOOLS", schoolIds = schools))
    }

    private fun fold(s: String): String = s.trim().lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
}
