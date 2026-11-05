package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.data.api.dto.CreateApproachRequest
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.domain.usecase.approach.AddApproachPinUseCase
import com.meteomontana.android.domain.usecase.approach.CreateApproachUseCase
import com.meteomontana.android.domain.usecase.approach.DeleteApproachUseCase
import com.meteomontana.android.domain.usecase.approach.GetApproachesUseCase
import javax.inject.Inject

/**
 * Aproximaciones parking → sector de una escuela (SOLO ADMIN por ahora, APPROACH_DESIGN.md §2.6/§10): leer, grabar un
 * camino con sus chinchetas, añadir una chincheta y borrar. Sacado de SchoolDetailViewModel (2026-11-05).
 */
class ApproachActions @Inject constructor(
    private val getApproachesUseCase: GetApproachesUseCase,
    private val createApproachUseCase: CreateApproachUseCase,
    private val addPinUseCase: AddApproachPinUseCase,
    private val deleteApproachUseCase: DeleteApproachUseCase
) {
    /** Los caminos de la escuela; vacío si falla. */
    suspend fun load(schoolId: String): List<Approach> = runCatching { getApproachesUseCase(schoolId) }.getOrDefault(emptyList())

    /** Sube el camino grabado y después sus chinchetas (si una falla, las demás siguen). */
    suspend fun create(schoolId: String, req: CreateApproachRequest, pins: List<AddApproachPinRequest>) {
        val approach = createApproachUseCase(schoolId, req)
        pins.forEach { runCatching { addPinUseCase(approach.id, it) } }
    }

    /** Añade una chincheta a un camino ya publicado. */
    suspend fun addPin(approachId: String, req: AddApproachPinRequest) { addPinUseCase(approachId, req) }

    /** Borra un camino entero (el servidor borra sus chinchetas). */
    suspend fun delete(approachId: String) { deleteApproachUseCase(approachId) }
}
