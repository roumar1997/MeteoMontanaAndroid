package com.meteomontana.android.weather

import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.usecase.favorites.GetFavoritesGridUseCase
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastByLocationUseCase
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.ui.screens.weather.WeatherUiState
import com.meteomontana.android.ui.screens.weather.WeatherViewModel
import io.mockk.coEvery
import io.mockk.every
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
 * WeatherViewModel: reglas que protege:
 *  - sin permiso de ubicación → estado NeedPermission (no error, no crash)
 *  - con permiso pero sin fix GPS → cae a Madrid (40.4168,-3.7038)
 *  - seleccionar favorita carga su forecast; "sin favorita" (null) vuelve a GPS
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val d = StandardTestDispatcher()
    private lateinit var getForecast: GetForecastUseCase
    private lateinit var getByLoc: GetForecastByLocationUseCase
    private lateinit var getFavorites: GetMyFavoritesUseCase
    private lateinit var getGrid: GetFavoritesGridUseCase
    private lateinit var location: LocationProvider

    private val current = Current(
        "t", 20.0, 40.0, 10.0, 0.0, 0, 10, null, 0.0, 0.0, true, 70, "ok", emptyList(), null)
    private fun forecast(name: String) = Forecast(
        "id", name, 40.0, -3.0, current, emptyList(), emptyList(), null, null)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        getForecast = mockk(); getByLoc = mockk(); getFavorites = mockk(relaxed = true)
        getGrid = mockk(relaxed = true); location = mockk(relaxed = true)
        coEvery { getFavorites() } returns emptyList()
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = WeatherViewModel(getForecast, getByLoc, getFavorites, getGrid, location)

    @Test fun `sin permiso de ubicacion pide permiso`() = runTest {
        every { location.hasPermission() } returns false
        val vm = vm(); advanceUntilIdle()
        assertEquals(WeatherUiState.NeedPermission, vm.state.value)
    }

    @Test fun `con permiso pero sin GPS cae a Madrid`() = runTest {
        every { location.hasPermission() } returns true
        coEvery { location.current() } returns null
        val slotLat = io.mockk.slot<Double>()
        coEvery { getByLoc(capture(slotLat), any(), any()) } returns forecast("Madrid")
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value is WeatherUiState.Success)
        assertEquals(40.4168, slotLat.captured, 0.0001)  // fallback Madrid
    }

    @Test fun `con GPS carga el forecast por ubicacion`() = runTest {
        every { location.hasPermission() } returns true
        coEvery { location.current() } returns UserLocation(41.0, 2.0)
        coEvery { getByLoc(41.0, 2.0, null) } returns forecast("Barcelona")
        val vm = vm(); advanceUntilIdle()
        val s = vm.state.value as WeatherUiState.Success
        assertEquals("Barcelona", s.forecast.schoolName)
    }

    @Test fun `seleccionar favorita carga su forecast`() = runTest {
        every { location.hasPermission() } returns true
        coEvery { location.current() } returns UserLocation(41.0, 2.0)
        coEvery { getByLoc(any(), any(), any()) } returns forecast("GPS")
        coEvery { getForecast("esc-9") } returns forecast("Zarzalejo")
        val vm = vm(); advanceUntilIdle()
        vm.selectFavorite("esc-9"); advanceUntilIdle()
        val s = vm.state.value as WeatherUiState.Success
        assertEquals("Zarzalejo", s.forecast.schoolName)
        assertEquals("esc-9", s.selectedFavoriteId)
    }
}
