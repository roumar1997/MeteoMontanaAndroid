package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.schools.ClearProcessionaryActiveNowUseCase
import com.meteomontana.android.domain.usecase.schools.ConfirmProcessionaryUseCase
import com.meteomontana.android.domain.usecase.schools.ReportProcessionaryActiveNowUseCase
import com.meteomontana.android.domain.usecase.schools.RetractProcessionaryUseCase
import com.meteomontana.android.domain.util.ProcessionaryOverrideStore
import com.meteomontana.android.domain.util.ProcessionarySeason
import javax.inject.Inject

/**
 * Los cuatro botones de la procesionaria de una escuela (todos se pueden deshacer). Cada uno llama al servidor y
 * devuelve la escuela como queda, o null si no hay nada que hacer; además avisa a la lista de Escuelas con el
 * [ProcessionaryOverrideStore]. Sacado de SchoolDetailViewModel (2026-11-05), como JournalTickController.
 */
class ProcessionaryActions @Inject constructor(
    private val confirmUseCase: ConfirmProcessionaryUseCase,
    private val retractUseCase: RetractProcessionaryUseCase,
    private val reportActiveNowUseCase: ReportProcessionaryActiveNowUseCase,
    private val clearActiveNowUseCase: ClearProcessionaryActiveNowUseCase
) {
    /** «Sí que hay en este sector»: marca la escuela para siempre. */
    suspend fun confirm(schoolId: String, s: School): School? {
        if (s.hasKnownProcessionary) return null
        confirmUseCase(schoolId)
        val alert = ProcessionarySeason.isInSeason() || s.processionaryActiveNowSet
        return s.copy(hasKnownProcessionary = true, processionaryAlertActive = alert).also { publish(schoolId, alert) }
    }

    /** Deshace «Sí que hay en este sector». */
    suspend fun retract(schoolId: String, s: School): School? {
        if (!s.hasKnownProcessionary) return null
        retractUseCase(schoolId)
        return s.copy(hasKnownProcessionary = false, processionaryAlertActive = false, processionaryActiveNowSet = false)
            .also { publish(schoolId, false) }
    }

    /** «Las he visto antes de tiempo»: activa la alarma ya, con caducidad. */
    suspend fun reportActiveNow(schoolId: String, s: School): School {
        reportActiveNowUseCase(schoolId)
        return s.copy(hasKnownProcessionary = true, processionaryAlertActive = true, processionaryActiveNowSet = true)
            .also { publish(schoolId, true) }
    }

    /** Deshace solo «Las he visto antes de tiempo» (no toca la confirmación permanente). */
    suspend fun clearActiveNow(schoolId: String, s: School): School? {
        if (!s.processionaryActiveNowSet) return null
        clearActiveNowUseCase(schoolId)
        val alert = s.hasKnownProcessionary && ProcessionarySeason.isInSeason()
        return s.copy(processionaryActiveNowSet = false, processionaryAlertActive = alert).also { publish(schoolId, alert) }
    }

    private fun publish(schoolId: String, alert: Boolean) = ProcessionaryOverrideStore.set(schoolId, alert)
}
