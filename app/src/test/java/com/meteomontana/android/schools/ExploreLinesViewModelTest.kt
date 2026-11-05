package com.meteomontana.android.schools

import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import com.meteomontana.android.ui.screens.schools.ExploreContext
import com.meteomontana.android.ui.screens.schools.ExploreLinesViewModel
import com.meteomontana.android.ui.screens.schools.ExploreSortBy
import com.meteomontana.android.ui.screens.schools.ExploreTab
import com.meteomontana.android.ui.screens.schools.StyleFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Pestaña «Vías/Bloques»: qué se pide al servidor y cómo se ordena, separado de la lista de escuelas. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreLinesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val exploreLines: ExploreLinesUseCase = mockk()

    private fun hit(school: String, line: String) =
        LineSearchHit(school, "Escuela $school", "b$line", "Piedra", line, "Vía $line", "6a", null, null, null, null)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm() = ExploreLinesViewModel(exploreLines, mockk(relaxed = true))

    @Test fun `fuera de la pestaña no se pide nada`() = runTest(dispatcher) {
        val vm = newVm()
        vm.setGradeMin("6a")
        advanceUntilIdle()
        coVerify(exactly = 0) { exploreLines(any()) }
    }

    @Test fun `manda al servidor los filtros comunes del contexto`() = runTest(dispatcher) {
        val sent = slot<LineExploreCriteria>()
        coEvery { exploreLines(capture(sent)) } returns emptyList()
        val vm = newVm()
        vm.setContext(ExploreContext(style = StyleFilter.Boulder, rockTypes = listOf("Granito"), maxDistanceKm = 30.0, lat = 1.0, lon = 2.0))
        vm.setTab(ExploreTab.Blocks)
        advanceUntilIdle()
        assertEquals("BOULDER", sent.captured.discipline)
        assertEquals(listOf("Granito"), sent.captured.rockTypes)
        assertEquals(30.0, sent.captured.maxDistanceKm)
        assertEquals(1.0, sent.captured.lat)
    }

    @Test fun `si solo cambian las notas no se vuelve a pedir`() = runTest(dispatcher) {
        coEvery { exploreLines(any()) } returns emptyList()
        val vm = newVm()
        vm.setTab(ExploreTab.Blocks)
        advanceUntilIdle()
        vm.setContext(ExploreContext(scoreBySchool = mapOf("A" to 90)))
        advanceUntilIdle()
        coVerify(exactly = 1) { exploreLines(any()) }
    }

    @Test fun `mejores condiciones ordena los grupos por la nota`() = runTest(dispatcher) {
        coEvery { exploreLines(any()) } returns listOf(hit("A", "1"), hit("B", "2"))
        val vm = newVm()
        backgroundScope.launch(dispatcher) { vm.groups.collect {} }
        vm.setContext(ExploreContext(scoreBySchool = mapOf("A" to 40, "B" to 95)))
        vm.setTab(ExploreTab.Blocks)
        vm.setSortBy(ExploreSortBy.BestConditions)
        advanceUntilIdle()
        assertEquals(listOf("B", "A"), vm.groups.first().map { it.schoolId })
    }
}
