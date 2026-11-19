package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymFilterEngine
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymOrigin
import com.meteomontana.android.domain.model.gym.GymSort
import com.meteomontana.android.domain.model.gym.OpeningSlot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GymFiltersTest {

    private val sputnik = gym(1, "Sputnik", chain = "Sputnik", discipline = GymDiscipline.BOTH, lat = 40.42, lon = -3.70)
    private val boulderOnly = gym(2, "Bloc Zone", discipline = GymDiscipline.BOULDER, lat = 40.50, lon = -3.60)
    private val ropeOnly = gym(3, "Cuerda Club", kind = GymKind.MUNICIPAL, discipline = GymDiscipline.ROPE, lat = 41.4, lon = 2.17, city = "Barcelona", province = "Barcelona")
    private val unknown = gym(4, "Misterio", discipline = GymDiscipline.UNKNOWN, lat = 37.4, lon = -6.0, city = "Cádiz", province = "Cádiz")
    private val freeWall = gym(5, "Muro del parque", kind = GymKind.FREE, discipline = GymDiscipline.BOULDER, lat = 40.41, lon = -3.71)
    private val freePrice = gym(6, "Polideportivo", kind = GymKind.MUNICIPAL, priceSingle = 0.0)
    private val hidden = gym(7, "Oculto", hidden = true)
    private val all = listOf(sputnik, boulderOnly, ropeOnly, unknown, freeWall, freePrice, hidden)

    private fun ids(f: GymFilters, origin: GymOrigin? = null, fav: Set<Long> = emptySet(), now: Instant? = null) =
        GymFilterEngine.apply(all, f, origin, fav, now).map { it.gym.id }

    @Test
    fun `todo junto en una sola capa, y oculta los cerrados`() {
        assertEquals(setOf(1L, 2, 3, 4, 5, 6), ids(GymFilters()).toSet())
    }

    @Test
    fun `de calle deja solo los muros de calle`() {
        assertEquals(listOf(5L), ids(GymFilters(onlyStreet = true)))
    }

    @Test
    fun `los cerrados no salen salvo que se pidan`() {
        val closed = gym(8, "Cerrado", closed = true)
        val list = all + closed
        assertEquals(false, GymFilterEngine.apply(list, GymFilters()).any { it.gym.id == 8L })
        assertEquals(true, GymFilterEngine.apply(list, GymFilters(showClosed = true)).any { it.gym.id == 8L })
        assertEquals(false, GymFilterEngine.chains(listOf(gym(9, chain = "X", closed = true))).contains("X"))
    }

    @Test
    fun `gratis incluye precio cero`() {
        assertEquals(setOf(5L, 6L), ids(GymFilters(onlyFree = true)).toSet())
        assertEquals(listOf(5L), ids(GymFilters(onlyStreet = true, onlyFree = true)))
    }

    @Test
    fun `bloque y cuerda incluyen ambos y excluyen lo desconocido`() {
        assertEquals(setOf(1L, 2), ids(GymFilters(boulder = true)).toSet().intersect(setOf(1L, 2, 3, 4)))
        assertEquals(setOf(1L, 3), ids(GymFilters(rope = true)).toSet().intersect(setOf(1L, 2, 3, 4)))
        assertEquals(setOf(1L, 6L), ids(GymFilters(boulder = true, rope = true)).toSet())
    }

    @Test
    fun `cadena sin distinguir mayusculas`() {
        assertEquals(listOf(1L), ids(GymFilters(chain = "sputnik")))
    }

    @Test
    fun `busqueda sin tildes por nombre o ciudad`() {
        assertEquals(listOf(4L), ids(GymFilters(query = "cadiz")))
        assertEquals(listOf(3L), ids(GymFilters(query = "BARCELONA")))
        assertEquals(listOf(2L), ids(GymFilters(query = "bloc")))
    }

    @Test
    fun `favoritos solo los marcados`() {
        assertEquals(listOf(2L), ids(GymFilters(onlyFavorites = true), fav = setOf(2L)))
        assertEquals(emptyList(), ids(GymFilters(onlyFavorites = true)))
    }

    @Test
    fun `abierto ahora exige horario y que este abierto`() {
        val open = gym(10, "Abierto", hours = listOf(OpeningSlot("MON-FRI", "10:00", "23:00")))
        val shut = gym(11, "Cerrado", hours = listOf(OpeningSlot("MON-FRI", "18:00", "23:00")))
        val noHours = gym(12, "Sin horario")
        val now = Instant.parse("2026-11-16T09:00:00Z") // lunes 10:00
        val r = GymFilterEngine.apply(listOf(open, shut, noHours), GymFilters(openNow = true), now = now)
        assertEquals(listOf(10L), r.map { it.gym.id })
    }

    @Test
    fun `orden por distancia y radio maximo`() {
        val madrid = GymOrigin(40.4168, -3.7038)
        val r = GymFilterEngine.apply(all, GymFilters(maxDistanceKm = 30.0), madrid)
        assertEquals(listOf(1L, 5L, 6L, 2L), r.map { it.gym.id })
        assertEquals(true, r.all { it.distanceKm!! <= 30.0 })
        assertEquals(true, r.map { it.distanceKm!! }.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun `orden por nombre y sin origen`() {
        val r = GymFilterEngine.apply(all, GymFilters(sort = GymSort.NAME))
        assertEquals(r.map { it.gym.name.lowercase() }.sorted(), r.map { it.gym.name.lowercase() })
        assertEquals(true, r.all { it.distanceKm == null })
    }

    @Test
    fun `un radio sin ningun rocodromo dentro se ignora, como en Escuelas`() {
        val newYork = GymOrigin(40.71, -74.0)
        assertEquals(setOf(1L, 2, 3, 4, 5, 6), GymFilterEngine.apply(all, GymFilters(maxDistanceKm = 50.0), newYork).map { it.gym.id }.toSet())
        val madrid = GymOrigin(40.4168, -3.7038)
        assertEquals(false, GymFilterEngine.apply(all, GymFilters(maxDistanceKm = 50.0), madrid).any { it.gym.id == 3L })
    }

    @Test
    fun `cadenas disponibles sin repetir`() {
        val two = listOf(gym(1, chain = "Sputnik"), gym(2, chain = "sputnik"), gym(3, chain = "Bloc"), gym(4, chain = null))
        assertEquals(listOf("Bloc", "Sputnik"), GymFilterEngine.chains(two))
    }
}
