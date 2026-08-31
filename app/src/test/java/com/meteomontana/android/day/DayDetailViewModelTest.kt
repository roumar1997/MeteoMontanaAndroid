package com.meteomontana.android.day

import androidx.lifecycle.SavedStateHandle
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.HourForecast
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.ui.screens.day.DayDetailUiState
import com.meteomontana.android.ui.screens.day.DayDetailViewModel
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
 * DayDetailViewModel: reglas que protege:
 *  - filtra las HORAS del día pedido por fecha ISO
 *  - título "Hoy"/"Mañana"/día-de-semana (Zeller) según el índice
 *  - día fuera de rango → Error controlado
 *  - sin red, cae al snapshot offline de la escuela
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var getForecast: GetForecastUseCase
    private lateinit var getByLoc: GetForecastByLocationUseCase
    private lateinit var savedRepo: SavedSchoolRepository

    private fun hour(time: String, score: Int) = HourForecast(
        time, 20.0, 40.0, 10.0, 0.0, 0, 10, null, score, "ok", 1)
    private fun day(date: String, score: Int) = DayForecast(date, 25.0, 12.0, 0.0, score, "ok")

    private val current = Current(
        "2026-07-19T10:00", 20.0, 40.0, 10.0, 0.0, 0, 10, null, 0.0, 0.0,
        true, 70, "ok", emptyList(), null)

    private fun forecast() = Forecast(
        schoolId = "esc", schoolName = "Zarzalejo", lat = 40.0, lon = -4.0, current = current,
        hours = listOf(
            hour("2026-07-19T10:00", 70), hour("2026-07-19T11:00", 72),
            hour("2026-07-20T10:00", 65)),
        days = listOf(day("2026-07-19", 71), day("2026-07-20", 66)),
        bestDay = null, bestWindow = null)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getForecast = mockk(); getByLoc = mockk(); savedRepo = mockk(relaxed = true)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(dayIndex: Int, schoolId: String? = "esc") = DayDetailViewModel(
        SavedStateHandle(mapOf("schoolId" to schoolId, "dayIndex" to dayIndex.toString())),
        getForecast, getByLoc, savedRepo)

    @Test fun `dia 0 se titula Hoy y trae solo las horas de ese dia`() = runTest {
        coEvery { getForecast("esc") } returns forecast()
        val vm = vm(0); advanceUntilIdle()
        val s = vm.state.value as DayDetailUiState.Loaded
        assertEquals("Hoy", s.title)
        assertEquals(2, s.hoursOfDay.size)  // solo las del 19, no la del 20
        assertTrue(s.hoursOfDay.all { it.time.startsWith("2026-07-19") })
    }

    @Test fun `dia 1 se titula Mañana`() = runTest {
        coEvery { getForecast("esc") } returns forecast()
        val vm = vm(1); advanceUntilIdle()
        assertEquals("Mañana", (vm.state.value as DayDetailUiState.Loaded).title)
    }

    @Test fun `dia fuera de rango produce Error`() = runTest {
        coEvery { getForecast("esc") } returns forecast()
        val vm = vm(9); advanceUntilIdle()
        assertTrue(vm.state.value is DayDetailUiState.Error)
    }

    @Test fun `sin red cae al snapshot offline de la escuela`() = runTest {
        coEvery { getForecast("esc") } throws java.net.UnknownHostException()
        coEvery { savedRepo.loadOffline("esc") } returns
            com.meteomontana.android.data.saved.OfflineSnapshot(
                school = mockk(relaxed = true), blocks = emptyList(), lines = emptyList(),
                forecast = forecast(), forecastFetchedAt = 0L)
        val vm = vm(0); advanceUntilIdle()
        assertTrue(vm.state.value is DayDetailUiState.Loaded)  // no Error: usó el snapshot
    }
}
