package com.meteomontana.android.schools

import app.cash.turbine.test
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.SchoolScore
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.model.SchoolCatalog
import com.meteomontana.android.domain.usecase.schools.GetSchoolCatalogUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.ui.screens.schools.SchoolListUiState
import com.meteomontana.android.ui.screens.schools.SchoolListViewModel
import com.meteomontana.android.ui.screens.schools.SortBy
import com.meteomontana.android.ui.screens.schools.StyleFilter
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
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

    private lateinit var getSchoolCatalog: GetSchoolCatalogUseCase
    private lateinit var etagStore: com.meteomontana.android.data.local.CatalogEtagStore
    private lateinit var getTodayScores: GetTodayScoresUseCase
    private lateinit var getRangeScores: GetRangeScoresUseCase
    private lateinit var getMyFavorites: GetMyFavoritesUseCase
    private lateinit var addFavorite: com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
    private lateinit var removeFavorite: com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
    private lateinit var getMyNotifications: GetMyNotificationsUseCase
    private lateinit var location: LocationProvider
    private lateinit var savedRepo: SavedSchoolRepository
    private lateinit var cachedRepo: CachedSchoolsRepository
    private lateinit var chatService: ChatService

    // A está a ~190 km de Madrid (queda fuera del radio por defecto de 50 km).
    private val schoolA = School(
        id = "A", name = "Albarracín", location = "Teruel", region = "Aragón",
        style = "Bloque", rockType = "Arenisca", lat = 40.408, lon = -1.444, source = null
    )
    // B está a ~40 km de Madrid (dentro del radio por defecto).
    private val schoolB = School(
        id = "B", name = "Pedriza", location = "Madrid", region = "Madrid",
        style = "Vía", rockType = "Granito", lat = 40.768, lon = -3.852, source = null
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getSchoolCatalog = mockk()
        etagStore = mockk(relaxed = true)
        getTodayScores = mockk()
        getRangeScores = mockk(relaxed = true)
        chatService = mockk(relaxed = true)
        every { chatService.observeMyConversations() } returns emptyFlow()
        getMyFavorites = mockk()
        addFavorite = mockk(relaxed = true)
        removeFavorite = mockk(relaxed = true)
        getMyNotifications = mockk()
        location = mockk()
        savedRepo = mockk(relaxed = true)
        cachedRepo = mockk()
        coEvery { location.current() } returns null
        coEvery { getMyFavorites() } returns emptyList()
        coEvery { getMyNotifications(any()) } returns Inbox(unreadCount = 0, items = emptyList())
        coEvery { getTodayScores(any()) } returns emptyList()
        coEvery { cachedRepo.load() } returns emptyList()
        coJustRun { cachedRepo.replaceAll(any()) }
        coEvery { etagStore.get() } returns null
        coEvery {
            getSchoolCatalog(any())
        } returns SchoolCatalog(schools = listOf(schoolA, schoolB), etag = "abc123")
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private val outbox: com.meteomontana.android.data.outbox.OutboxRepository = mockk(relaxed = true)
    private val getPublicProfile: com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase = mockk(relaxed = true)
    private val appContext: android.content.Context = mockk(relaxed = true)

    private fun newVm() = SchoolListViewModel(
        getSchoolCatalog, getTodayScores, getRangeScores, getMyFavorites, addFavorite, removeFavorite,
        getMyNotifications, location, savedRepo, cachedRepo, etagStore, chatService, outbox, getPublicProfile, appContext
    )

    @Test fun `init baja el catalogo completo sin filtros y filtra en local`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("estado debe ser Success, era $state", state is SchoolListUiState.Success)
        // Con el radio por defecto de 50 km desde Madrid solo entra Pedriza.
        assertEquals(listOf("B"), (state as SchoolListUiState.Success).schools.map { it.id })
        // La red se pide UNA vez; sin caché previa no se manda ETag.
        coVerify(exactly = 1) { getSchoolCatalog(null) }
        // Y el catálogo fresco se persiste en la caché local.
        coVerify { cachedRepo.replaceAll(listOf(schoolA, schoolB)) }
    }

    @Test fun `con cache previa pinta datos aunque la red falle`() = runTest {
        coEvery { cachedRepo.load() } returns listOf(schoolB)
        coEvery { getSchoolCatalog(any()) } throws RuntimeException("sin red")
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe ser Success con datos de cache, era $state", state is SchoolListUiState.Success)
        assertEquals(listOf("B"), (state as SchoolListUiState.Success).schools.map { it.id })
    }

    @Test fun `fallback a Madrid si LocationProvider devuelve null`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        assertNull(vm.userLocation.value)
        val dPedriza = vm.distanceTo(40.768, -3.852)
        assertTrue("distance to Pedriza esperada >30 km, fue $dPedriza", dPedriza in 30.0..50.0)
    }

    @Test fun `onLocationGranted actualiza userLocation y refiltra sin red`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        // Usuario en Teruel: ahora la cercana es Albarracín, no Pedriza.
        coEvery { location.current() } returns UserLocation(40.40, -1.40)
        vm.onLocationGranted()
        advanceUntilIdle()

        assertEquals(UserLocation(40.40, -1.40), vm.userLocation.value)
        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals(listOf("A"), schools.map { it.id })
        // El refiltrado es local: la red sigue habiéndose llamado solo una vez.
        coVerify(exactly = 1) { getSchoolCatalog(any()) }
    }

    @Test fun `setStyle Via filtra en local por estilo`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setDistance(null)   // sin límite para ver ambas
        vm.setStyle(StyleFilter.Via)
        advanceUntilIdle()

        assertEquals(StyleFilter.Via, vm.filters.value.style)
        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals(listOf("B"), schools.map { it.id })
    }

    @Test fun `setDistance null muestra todas sin llamar a red`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setDistance(null)
        advanceUntilIdle()

        assertNull(vm.filters.value.maxDistanceKm)
        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals(2, schools.size)
        coVerify(exactly = 1) { getSchoolCatalog(any()) }
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
            SchoolScore(id = "A", todayScore = 30, hourlyScores = emptyList(), dryRock = true),
            SchoolScore(id = "B", todayScore = 90, hourlyScores = emptyList(), dryRock = true)
        )
        val vm = newVm()
        advanceUntilIdle()

        vm.setDistance(null)   // sin límite para ver ambas
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

        vm.setDistance(null)
        vm.setSort(SortBy.Distance)
        advanceUntilIdle()

        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals("B", schools[0].id)
        assertEquals("A", schools[1].id)
    }

    @Test fun `setOnlyFavorites true filtra por ids favoritos`() = runTest {
        coEvery { getMyFavorites() } returns listOf(
            FavoriteSchool(id = "A", name = "Albarracín", region = null, rockType = null, isFavorite = true)
        )
        val vm = newVm()
        advanceUntilIdle()

        vm.setDistance(null)   // A está a >50 km del fallback Madrid
        vm.setOnlyFavorites(true)
        advanceUntilIdle()

        val schools = (vm.uiState.value as SchoolListUiState.Success).schools
        assertEquals(1, schools.size)
        assertEquals("A", schools.first().id)
    }

    @Test fun `304 reusa la cache local sin reemplazarla`() = runTest {
        coEvery { cachedRepo.load() } returns listOf(schoolB)
        coEvery { etagStore.get() } returns "abc123"
        coEvery { getSchoolCatalog("abc123") } returns SchoolCatalog(schools = null, etag = "abc123")
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe ser Success desde cache, era $state", state is SchoolListUiState.Success)
        assertEquals(listOf("B"), (state as SchoolListUiState.Success).schools.map { it.id })
        // Se mandó el ETag guardado y, al responder 304, la caché no se pisa.
        coVerify(exactly = 1) { getSchoolCatalog("abc123") }
        coVerify(exactly = 0) { cachedRepo.replaceAll(any()) }
    }

    @Test fun `error de red sin cache produce estado Error con mensaje`() = runTest {
        coEvery { getSchoolCatalog(any()) } throws RuntimeException("boom")
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe ser Error, fue $state", state is SchoolListUiState.Error)
        assertEquals("boom", (state as SchoolListUiState.Error).message)
    }

    @Test fun `unreadCount se actualiza desde getMyNotifications`() = runTest {
        coEvery { getMyNotifications(any()) } returns Inbox(unreadCount = 7, items = emptyList())
        val vm = newVm()
        advanceUntilIdle()

        vm.unreadCount.test {
            assertEquals(7L, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
