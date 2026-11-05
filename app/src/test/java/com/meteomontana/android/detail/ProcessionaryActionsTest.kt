package com.meteomontana.android.detail

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.schools.ClearProcessionaryActiveNowUseCase
import com.meteomontana.android.domain.usecase.schools.ConfirmProcessionaryUseCase
import com.meteomontana.android.domain.usecase.schools.ReportProcessionaryActiveNowUseCase
import com.meteomontana.android.domain.usecase.schools.RetractProcessionaryUseCase
import com.meteomontana.android.ui.screens.detail.ProcessionaryActions
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los cuatro botones de la procesionaria: qué llaman y cómo dejan la escuela. */
class ProcessionaryActionsTest {

    private val confirm: ConfirmProcessionaryUseCase = mockk(relaxed = true)
    private val retract: RetractProcessionaryUseCase = mockk(relaxed = true)
    private val report: ReportProcessionaryActiveNowUseCase = mockk(relaxed = true)
    private val clear: ClearProcessionaryActiveNowUseCase = mockk(relaxed = true)
    private val actions = ProcessionaryActions(confirm, retract, report, clear)

    private val school = School(
        id = "s1", name = "Pedriza", location = null, region = null,
        style = "Bloque", rockType = "Granito", lat = 40.0, lon = -3.0, source = null
    )

    @Test fun `confirmar una escuela ya confirmada no llama a nadie`() = runTest {
        assertNull(actions.confirm("s1", school.copy(hasKnownProcessionary = true)))
        coVerify(exactly = 0) { confirm(any()) }
    }

    @Test fun `retirar la confirmacion apaga todo`() = runTest {
        val after = actions.retract("s1", school.copy(hasKnownProcessionary = true, processionaryAlertActive = true, processionaryActiveNowSet = true))!!
        assertEquals(false, after.hasKnownProcessionary)
        assertEquals(false, after.processionaryAlertActive)
        assertEquals(false, after.processionaryActiveNowSet)
        coVerify(exactly = 1) { retract("s1") }
    }

    @Test fun `verlas antes de tiempo activa la alarma ya`() = runTest {
        val after = actions.reportActiveNow("s1", school)
        assertEquals(true, after.processionaryAlertActive)
        assertEquals(true, after.processionaryActiveNowSet)
        assertEquals(true, after.hasKnownProcessionary)
    }

    @Test fun `quitar el antes de tiempo sin haberlo puesto no hace nada`() = runTest {
        assertNull(actions.clearActiveNow("s1", school))
        coVerify(exactly = 0) { clear(any()) }
    }
}
