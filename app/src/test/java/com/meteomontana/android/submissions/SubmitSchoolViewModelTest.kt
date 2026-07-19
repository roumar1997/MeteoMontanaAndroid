package com.meteomontana.android.submissions

import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.submissions.SubmitSchoolUseCase
import com.meteomontana.android.ui.screens.submissions.SubmitSchoolViewModel
import com.meteomontana.android.ui.screens.submissions.SubmitState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SubmitSchoolViewModel: opciones de los desplegables (únicas, sin blancos,
 * ordenadas) y filtro de localidades por región. Evita erratas y duplicados en
 * las propuestas de escuela nueva.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitSchoolViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var submit: SubmitSchoolUseCase
    private lateinit var getSchools: GetSchoolsUseCase

    private fun school(id: String, region: String?, style: String?, rock: String?, loc: String?) =
        School(id, "N$id", loc, region, style, rock, 40.0, -3.0, null)

    private val catalog = listOf(
        school("1", "Madrid", "Bloque", "Granito", "Zarzalejo"),
        school("2", "Madrid", "Vía", "Granito", "La Pedriza"),
        school("3", "Aragón", "Bloque", "Arenisca", "Albarracín"),
        school("4", "Madrid", "Bloque", null, "  "),   // rock null y loc en blanco → se filtran
    )

    @Before fun setUp() {
        Dispatchers.setMain(d)
        submit = mockk(); getSchools = mockk()
        coEvery { getSchools() } returns catalog
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SubmitSchoolViewModel(submit, getSchools)

    @Test fun `opciones son unicas ordenadas y sin blancos`() = runTest {
        val vm = vm(); advanceUntilIdle()
        val o = vm.options.value
        assertEquals(listOf("Aragón", "Madrid"), o.regions)
        assertEquals(listOf("Bloque", "Vía"), o.styles)
        assertEquals(listOf("Arenisca", "Granito"), o.rockTypes)  // el null fuera
    }

    @Test fun `locationOptions filtra por region ignorando mayusculas`() = runTest {
        val vm = vm(); advanceUntilIdle()
        val madrid = vm.locationOptions("madrid")
        assertEquals(listOf("La Pedriza", "Zarzalejo"), madrid)  // Albarracín es Aragón; blanco fuera
    }

    @Test fun `locationOptions con region vacia devuelve todas`() = runTest {
        val vm = vm(); advanceUntilIdle()
        assertEquals(listOf("Albarracín", "La Pedriza", "Zarzalejo"), vm.locationOptions(""))
    }

    @Test fun `submit exitoso pasa a Done`() = runTest {
        coEvery { submit(any()) } returns mockk(relaxed = true)
        val vm = vm(); advanceUntilIdle()
        vm.submit(mockk<SubmitSchoolRequest>(relaxed = true)); advanceUntilIdle()
        assertEquals(SubmitState.Done, vm.state.value)
    }

    @Test fun `submit con error pasa a Error`() = runTest {
        coEvery { submit(any()) } throws RuntimeException("boom")
        val vm = vm(); advanceUntilIdle()
        vm.submit(mockk<SubmitSchoolRequest>(relaxed = true)); advanceUntilIdle()
        assertTrue(vm.state.value is SubmitState.Error)
    }
}
