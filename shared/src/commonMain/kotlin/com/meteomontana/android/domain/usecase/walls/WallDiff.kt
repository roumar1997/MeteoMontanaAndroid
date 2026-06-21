package com.meteomontana.android.domain.usecase.walls

/**
 * Calcula el DIFF entre el estado ACTUAL de un muro (sus vías, en orden) y el
 * estado COMPLETO propuesto por una contribución, para que el admin vea QUÉ
 * cambia antes de aprobar (no aceptar/rechazar a ciegas).
 *
 * Espejo EXACTO de `WallDiffCalculator` del backend (Java). Lógica PURA: el
 * llamante adapta el `Block` actual + el `bloquesJson` propuesto a estas listas.
 * Las vías existentes se identifican por `id` estable; las propuestas referencian
 * ese `id` (o `null` si son nuevas). Eso distingue NUEVA / MOVIDA / MODIFICADA /
 * QUITADA / CONFLICTO.
 */

/** Vía existente en el muro actual (en su orden actual). */
data class ExistingRoute(val id: String, val name: String?, val grade: String?)

/** Vía de la propuesta (en el orden propuesto). lineId=null → vía nueva. */
data class ProposedRoute(val lineId: String?, val name: String?, val grade: String?)

enum class WallRouteStatus { NEW, SAME, MOVED, MODIFIED, MOVED_MODIFIED, REMOVED, CONFLICT }

data class WallRouteChange(
    val status: WallRouteStatus,
    val lineId: String?,     // id de la vía (null si NEW)
    val name: String?,
    val oldGrade: String?,
    val newGrade: String?,
    val oldPos: Int?,        // posición (0-based) en el muro actual; null si NEW
    val newPos: Int?         // posición propuesta; null si REMOVED
)

data class WallDiff(
    val pathChanged: Boolean,
    val oldDirection: String?,
    val newDirection: String?,
    val proposed: List<WallRouteChange>,   // en orden propuesto
    val removed: List<WallRouteChange>     // existentes que la propuesta omite
)

object WallDiffCalculator {

    fun compute(
        existing: List<ExistingRoute>,
        proposed: List<ProposedRoute>,
        pathChanged: Boolean,
        oldDirection: String?,
        newDirection: String?
    ): WallDiff {
        val oldPosById = LinkedHashMap<String, Int>()
        val oldById = LinkedHashMap<String, ExistingRoute>()
        existing.forEachIndexed { i, r ->
            oldPosById[r.id] = i
            oldById[r.id] = r
        }

        val proposedChanges = ArrayList<WallRouteChange>()
        val referenced = HashSet<String>()
        proposed.forEachIndexed { newPos, p ->
            val id = p.lineId
            if (id.isNullOrBlank()) {
                proposedChanges.add(WallRouteChange(WallRouteStatus.NEW, null, p.name, null, p.grade, null, newPos))
                return@forEachIndexed
            }
            val old = oldById[id]
            if (old == null) {
                // Referencia una vía que ya no existe (cambió mientras tanto).
                proposedChanges.add(WallRouteChange(WallRouteStatus.CONFLICT, id, p.name, null, p.grade, null, newPos))
                return@forEachIndexed
            }
            referenced.add(id)
            val oldPos = oldPosById.getValue(id)
            val moved = oldPos != newPos
            val modified = !eq(old.grade, p.grade) || !eq(old.name, p.name)
            val st = when {
                moved && modified -> WallRouteStatus.MOVED_MODIFIED
                moved -> WallRouteStatus.MOVED
                modified -> WallRouteStatus.MODIFIED
                else -> WallRouteStatus.SAME
            }
            proposedChanges.add(WallRouteChange(st, id, p.name, old.grade, p.grade, oldPos, newPos))
        }

        val removed = ArrayList<WallRouteChange>()
        existing.forEachIndexed { i, r ->
            if (r.id !in referenced) {
                removed.add(WallRouteChange(WallRouteStatus.REMOVED, r.id, r.name, r.grade, null, i, null))
            }
        }

        return WallDiff(pathChanged, oldDirection, newDirection, proposedChanges, removed)
    }

    private fun eq(a: String?, b: String?): Boolean =
        (a?.trim() ?: "") == (b?.trim() ?: "")
}
