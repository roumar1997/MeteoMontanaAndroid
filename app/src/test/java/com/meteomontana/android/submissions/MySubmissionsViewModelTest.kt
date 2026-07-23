package com.meteomontana.android.submissions

import com.meteomontana.android.domain.usecase.contributions.GetMyContributionsUseCase
import com.meteomontana.android.domain.usecase.submissions.GetMySubmissionsUseCase
import com.meteomontana.android.ui.screens.submissions.MySubmissionsUiState
import com.meteomontana.android.ui.screens.submissions.MySubmissionsViewModel
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
 * MySubmissionsViewModel: si una de las dos listas (propuestas / contribuciones)
 * falla, la otra SIGUE mostrándose (runCatching por separado) — no se pierde
 * todo por un fallo parcial.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MySubmissionsViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var getSubs: GetMySubmissionsUseCase
    private lateinit var getContribs: GetMyContributionsUseCase

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getSubs = mockk(); getContribs = mockk()
    }
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga ambas listas`() = runTest {
        coEvery { getSubs() } returns List(2) { mockk(relaxed = true) }
        coEvery { getContribs() } returns List(3) { mockk(relaxed = true) }
        val vm = MySubmissionsViewModel(getSubs, getContribs); advanceUntilIdle()
        val s = vm.state.value as MySubmissionsUiState.Success
        assertEquals(2, s.submissions.size)
        assertEquals(3, s.contributions.size)
    }

    @Test fun `si las contribuciones fallan, las propuestas siguen visibles`() = runTest {
        coEvery { getSubs() } returns List(2) { mockk(relaxed = true) }
        coEvery { getContribs() } throws RuntimeException("500")
        val vm = MySubmissionsViewModel(getSubs, getContribs); advanceUntilIdle()
        val s = vm.state.value as MySubmissionsUiState.Success
        assertEquals(2, s.submissions.size)
        assertTrue(s.contributions.isEmpty())
    }
}
