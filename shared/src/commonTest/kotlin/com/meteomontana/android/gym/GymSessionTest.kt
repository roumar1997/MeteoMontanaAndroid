package com.meteomontana.android.gym

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.repository.LocalFirstGymSessionRepository
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymStatsCalculator
import com.meteomontana.android.domain.port.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GymSessionTest {

    // Miercoles 2026-11-18
    private val today = LocalDate(2026, 11, 18)

    private fun s(gym: Long, y: Int, m: Int, d: Int) = GymSession(gym, LocalDate(y, m, d))

    @Test
    fun `sin entrenos todo a cero`() {
        val stats = GymStatsCalculator.compute(emptyList(), today)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.streakWeeks)
    }

    @Test
    fun `cuenta semana, mes y año`() {
        val sessions = listOf(
            s(1, 2026, 11, 16),  // lunes de esta semana
            s(1, 2026, 11, 18),  // hoy
            s(2, 2026, 11, 3),   // este mes, otra semana
            s(2, 2026, 2, 10),   // este año
            s(3, 2025, 12, 30)   // el año pasado
        )
        val st = GymStatsCalculator.compute(sessions, today)
        assertEquals(2, st.week)
        assertEquals(3, st.month)
        assertEquals(4, st.year)
    }

    @Test
    fun `el domingo todavia es de la semana anterior y el lunes abre la nueva`() {
        val st = GymStatsCalculator.compute(listOf(s(1, 2026, 11, 15), s(1, 2026, 11, 16)), today)
        assertEquals(1, st.week)
    }

    @Test
    fun `rocodromo mas visitado del año, y empate a favor del mas reciente`() {
        val sessions = listOf(s(1, 2026, 1, 5), s(1, 2026, 1, 12), s(2, 2026, 3, 1), s(2, 2026, 5, 2), s(3, 2026, 6, 1))
        val st = GymStatsCalculator.compute(sessions, today)
        assertEquals(2L, st.topGymId)
        assertEquals(2, st.topGymVisits)
    }

    @Test
    fun `la racha cuenta semanas seguidas y la semana en curso vacia no la corta`() {
        val sessions = listOf(s(1, 2026, 11, 10), s(1, 2026, 11, 3), s(1, 2026, 10, 27), s(1, 2026, 10, 6))
        // Esta semana (16-22 nov) aun sin entreno: cuentan 10 nov, 3 nov y 27 oct; el hueco de 13 oct corta.
        assertEquals(3, GymStatsCalculator.compute(sessions, today).streakWeeks)
        assertEquals(4, GymStatsCalculator.compute(sessions + s(2, 2026, 11, 17), today).streakWeeks)
    }

    @Test
    fun `dos entrenos el mismo dia en el mismo rocodromo cuentan una vez`() {
        val st = GymStatsCalculator.compute(listOf(s(1, 2026, 11, 18), s(1, 2026, 11, 18)), today)
        assertEquals(1, st.year)
    }

    private class MemStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private fun repo(store: KeyValueStore) = LocalFirstGymSessionRepository(
        KtorGymSessionApi(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })), store, { today }
    )

    @Test
    fun `apuntar funciona aunque el servidor no tenga la ruta, no duplica y se puede deshacer`() = runTest {
        val store = MemStore()
        val r = repo(store)
        r.checkInToday(7, GymSessionSource.MANUAL)
        r.checkInToday(7, GymSessionSource.PRESENCE)
        assertEquals(1, r.sessions().size)
        assertEquals(today, r.sessions().single().date)
        // sobrevive a un reinicio (otro repositorio sobre el mismo almacen)
        assertEquals(1, repo(store).sessions().size)
        r.undoToday(7)
        assertFalse(r.sessions().any { it.gymId == 7L })
    }

    @Test
    fun `editar un entreno a cero borra la sesion pero uno sin nada apuntado se queda`() = runTest {
        val r = repo(MemStore())
        r.checkInToday(7, GymSessionSource.MANUAL)
        // sin nada apuntado, guardar vacio no la borra (es el entreno de Aqui)
        r.saveAscents(7, today, emptyList())
        assertEquals(1, r.sessions().size)
        r.saveAscents(7, today, listOf(com.meteomontana.android.domain.model.gym.GymAscent("6a", 2)))
        assertEquals(2, r.sessions().single().ascents.single().count)
        // a cero tras haber apuntado algo: la sesion desaparece
        r.saveAscents(7, today, emptyList())
        assertTrue(r.sessions().isEmpty())
    }

    @Test
    fun `refresh sin servidor devuelve lo local`() = runTest {
        val r = repo(MemStore())
        r.checkInToday(1, GymSessionSource.MANUAL)
        assertEquals(1, r.refresh().size)
    }
}
