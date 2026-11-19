package com.meteomontana.android.gyms

import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.usecase.gym.ClusterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import com.meteomontana.android.domain.usecase.gym.SetGymFavoriteUseCase
import com.meteomontana.android.ui.screens.gyms.GymsViewModel
import com.meteomontana.android.ui.screens.gyms.SchoolsLayer
import com.meteomontana.android.ui.screens.gyms.gymActiveFilterCount
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Capa de rocódromos de Escuelas: selector, filtros, favoritos y fallo sin red. */
@OptIn(ExperimentalCoroutinesApi::class)
class GymsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val cached = mockk<GetCachedGymsUseCase>()
    private val getGyms = mockk<GetGymsUseCase>()
    private val getFavorites = mockk<GetGymFavoritesUseCase>()
    private val setFavorite = mockk<SetGymFavoriteUseCase>()

    private fun gym(id: Long, kind: GymKind, discipline: GymDiscipline = GymDiscipline.BOTH, chain: String? = null) = Gym(
        id = id, slug = "g$id", name = "Gym $id", chain = chain, kind = kind, discipline = discipline, region = "Madrid",
        province = "Madrid", city = "Madrid", address = null, lat = 40.4 + id / 100.0, lon = -3.7, website = null,
        instagram = null, openingHours = null, priceSingleEur = null, pricePassEur = null, pricePassLabel = null,
        priceUpdatedAt = null, hasAutobelay = null, hasGearRental = null, kidsFriendly = null, beginnerFriendly = null,
        outdoor = kind == GymKind.FREE, verification = GymVerification.UNCONFIRMED, source = null, hidden = false, updatedAt = null
    )

    private val catalog = listOf(
        gym(1, GymKind.COMMERCIAL, GymDiscipline.BOULDER, chain = "Sputnik"),
        gym(2, GymKind.MUNICIPAL, GymDiscipline.ROPE),
        gym(3, GymKind.FREE, GymDiscipline.BOULDER)
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { cached.invoke() } returns emptyList()
        coEvery { getGyms.execute() } returns catalog
        coEvery { getFavorites.execute() } returns emptySet()
        coEvery { setFavorite.execute(any(), any()) } returns true
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = GymsViewModel(cached, getGyms, FilterGymsUseCase(), ClusterGymsUseCase(), getFavorites, setFavorite)

    private fun TestScope.observe(vm: GymsViewModel) = backgroundScope.launch { vm.state.collect {} }

    @Test fun `rocodromos trae todo y De calle deja solo los muros de calle`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L), vm.state.value.items.map { it.gym.id }.toSet())
        vm.toggleStreet(); advanceUntilIdle()
        assertEquals(listOf(3L), vm.state.value.items.map { it.gym.id })
    }

    @Test fun `la distancia arranca en 50 km como Escuelas y BORRAR la deja en la base`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        assertEquals(50.0, vm.state.value.filters.maxDistanceKm)
        assertEquals(0, gymActiveFilterCount(vm.state.value.filters))
        vm.setMaxDistance(null); vm.toggleStreet(); vm.toggleRope(); advanceUntilIdle()
        assertEquals(3, gymActiveFilterCount(vm.state.value.filters))
        vm.clearFilters(); advanceUntilIdle()
        assertEquals(0, gymActiveFilterCount(vm.state.value.filters))
    }

    @Test fun `la capa roca no carga nada`() = runTest(dispatcher) {
        val vm = vm(); observe(vm); advanceUntilIdle()
        assertTrue(vm.state.value.all.isEmpty())
        assertEquals(SchoolsLayer.ROCK, vm.layer.value)
    }

    @Test fun `filtros de bloque y cuerda`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        vm.toggleRope(); advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.items.map { it.gym.id })
        vm.toggleRope(); vm.toggleBoulder(); advanceUntilIdle()
        assertEquals(listOf(1L, 3L), vm.state.value.items.map { it.gym.id })
    }

    @Test fun `volver a Roca y a Rocodromos conserva los filtros`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        vm.toggleFree(); vm.setChain("Sputnik"); advanceUntilIdle()
        vm.setLayer(SchoolsLayer.ROCK); vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        assertTrue(vm.state.value.filters.onlyFree)
        assertEquals("Sputnik", vm.state.value.filters.chain)
    }

    @Test fun `sin red y sin copia local queda en fallo`() = runTest(dispatcher) {
        coEvery { getGyms.execute() } throws RuntimeException("sin red")
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        assertTrue(vm.state.value.loadFailed)
        assertTrue(vm.state.value.all.isEmpty())
    }

    @Test fun `sin red pero con copia local se ve la copia`() = runTest(dispatcher) {
        every { cached.invoke() } returns catalog
        coEvery { getGyms.execute() } throws RuntimeException("sin red")
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test fun `favorito se marca y se deshace si el servidor falla`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS); advanceUntilIdle()
        vm.toggleFavorite(1); advanceUntilIdle()
        assertEquals(setOf(1L), vm.state.value.favorites)
        coEvery { setFavorite.execute(any(), any()) } throws RuntimeException("sin red")
        vm.toggleFavorite(2); advanceUntilIdle()
        assertEquals(setOf(1L), vm.state.value.favorites)
    }

    @Test fun `con origen la lista sale ordenada por distancia`() = runTest(dispatcher) {
        val vm = vm(); observe(vm)
        vm.setLayer(SchoolsLayer.ROCODROMOS)
        vm.setOrigin(40.42, -3.7); advanceUntilIdle()
        val items = vm.state.value.items
        assertEquals(listOf(2L, 3L, 1L), items.map { it.gym.id })
        assertTrue(items.all { it.distanceKm != null })
    }
}
