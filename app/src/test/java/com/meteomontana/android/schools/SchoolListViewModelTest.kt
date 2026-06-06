package com.meteomontana.android.schools

import app.cash.turbine.test
import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.data.api.dto.InboxDto
import com.meteomontana.android.data.api.dto.SchoolScoreDto
import com.meteomontana.android.data.location.LocationProvider
import com.meteomontana.android.data.location.UserLocation
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.ui.screens.schools.SchoolListUiState
import com.meteomontana.android.ui.screens.schools.SchoolListViewModel
import com.meteomontana.android.ui.screens.schools.SortBy
import com.meteomontana.android.ui.screens.schools.StyleFilter
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getSchools: GetSchoolsUseCase
    private lateinit var getTodayScores: GetTodayScoresUseCase
    private lateinit var getMyFavorites: GetMyFavoritesUseCase
    private lateinit var getMyNotifications: GetMyNotificationsUseCase
    private lateinit var location: LocationProvider

    private val schoolA = School(
        id = "A", name = "Albarracín", location = "Teruel", region = "Aragón",
        style = "Bloque", rockType = "Arenisca", lat = 40.408, lon = -1.444, source = null
    )
    private val schoolB = School(
        id = "B", name = "Pedriza", location = "Madrid", region = "Madrid",
        style = "Vía", rockType = "Granito", lat = 40.768, lon = -3.852, source = null
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getSchools = mockk()
        getTodayScores = mockk()
        getMyFavorites = mockk()
        getMyNotifications = mockk()
        location = mockk()
        coEvery { location.current() } returns null
        coEvery { getMyFavorites() } returns emptyList()
        coEvery { getMyNotifications(any()) } returns InboxDto(unreadCount = 0, items = emptyList())
        coEvery { getTodayScores(any()) } returns emptyList()
        coEvery {
            getSchools(any(), any(), any(), any(), any(), any())
        } returns listOf(schoolA, schoolB)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm() = SchoolListViewModel(
        getSchools, getTodayScores, getMyFavorites, getMyNotifications, location
    )

    @Test fun `init carga con filtros por defecto y produce Success`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("estado debe ser Success, era $state", state is SchoolListUiState.Success)
        assertEquals(2, (state as SchoolListUiState.Success).schools.size)
        coVerify {
            getSchools(
                region = null, style = null, rockType = null,
                lat = 40.4168, lon = -3.7038, radioKm = 50.0
            )
        }
    }

    @Test fun `fallback a Madrid si LocationProvider devuelve null`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        assertNull(vm.userLocation.value)
        val dPedriza = vm.distanceTo(40.768, -3.852)
        assertTrue("distance to Pedriza esperada >30 km, fue $dPedriza", dPedriza in 30.0..50.0)
    }

    @Test fun `onLocationGranted actualiza userLocation y recarga`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        coEvery { location.current() } returns UserLocation(41.0, 2.0)
        vm.onLocationGranted()
        advanceUntilIdle()

        assertEquals(UserLocation(41.0, 2.0), vm.userLocation.value)
        coVerify(atLeast = 2) { getSchools(any(), any(), any(), any(), any(), any()) }
        coVerify {
            getSchools(
                region = null, style = null, rockType = null,
                lat = 41.0, lon = 2.0, radioKm = 50.0
            )
        }
    }

    @Test fun `setStyle Via recarga con style=Via`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setStyle(StyleFilter.Via)
        advanceUntilIdle()

        coVerify {
            getSchools(
                region = null, style = "Vía", rockType = null,
                lat = 40.4168, lon = -3.7038, radioKm = 50.0
            )
        }
        assertEquals(StyleFilter.Via, vm.filters.value.style)
    }

    @Test fun `setDistance null pide sin radio y sin lat lon`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setDistance(null)
        advanceUntilIdle()

        coVerify {
            getSchools(
                region = null, style = null, rockType = null,
                lat = null, lon = null, radioKm = null
            )
        }
        assertNull(vm.filters.value.maxDistanceKm)
    }

    @Test fun `toggleRock anyade y vuelve a togglear lo quita`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.toggleRock("Granito")
        advanceUntilIdle()
        assertEquals(listOf("Granito"), vm.filters.value.rockTypes)

        vm.toggleRock("Granito")
        advanceUntilIdle()
        assertTrue(vm.filters.value.rockTypes.isEmpty())
    }

    @Test fun `setQuery filtra la lista por nombre o ubicacion`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setQuery("pedriza")
        advanceUntilIdle()

        val state = vm.uiState.value as SchoolListUiState.Success
        assertEquals(1, state.schools.size)
        assertEquals("B", state.schools.first().id)
    }

    @Test fun `tras llegar scores el sort por Score reordena`() = runTest {
        coEvery { getTodayScores(any()) } returns listOf(
            SchoolScoreDto(id = "A", todayScore = 30, hourlyScores = emptyList(), dryRock = true),
            SchoolScoreDto(id = "B", todayScore = 90, hourlyScores = emptyList(), dryRock = true)
        )
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SchoolListUiState.Success)
        val schools = (state as SchoolListUiState.Success).schools
        assertEquals("B", schools[0].id)
        assertEquals("A", schools[1].id)
        assertEquals(SortBy.Score, vm.filters.value.sortBy)
    }

    @Test fun `setSort Distance ordena por distancia desde el usuario`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setSort(SortBy.Distance)
        advanceUntilIdle()

        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals("B", schools[0].id)
        assertEquals("A", schools[1].id)
    }

    @Test fun `setOnlyFavorites true filtra por ids favoritos`() = runTest {
        coEvery { getMyFavorites() } returns listOf(
            FavoriteSchoolDto(id = "A", name = "Albarracín", region = null, rockType = null, isFavorite = true)
        )
        val vm = newVm()
        advanceUntilIdle()

        vm.setOnlyFavorites(true)
        advanceUntilIdle()

        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals(1, schools.size)
        assertEquals("A", schools.first().id)
    }

    @Test fun `error del repo produce estado Error con mensaje`() = runTest {
        coEvery { getSchools(any(), any(), any(), any(), any(), any()) } throws RuntimeException("boom")
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe ser Error, fue $state", state is SchoolListUiState.Error)
        assertEquals("boom", (state as SchoolListUiState.Error).message)
    }

    @Test fun `unreadCount se actualiza desde getMyNotifications`() = runTest {
        coEvery { getMyNotifications(any()) } returns InboxDto(unreadCount = 7, items = emptyList())
        val vm = newVm()
        advanceUntilIdle()

        vm.unreadCount.test {
            assertEquals(7L, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
