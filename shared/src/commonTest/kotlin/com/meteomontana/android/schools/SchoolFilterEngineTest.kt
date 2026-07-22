package com.meteomontana.android.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.schools.SchoolFilterEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Red de seguridad del filtrado/orden del catálogo (extraído del ViewModel).
 * Cubre las reglas que el usuario nota: el texto manda sobre los filtros, el
 * radio, el estilo/roca, favoritos, y los dos órdenes.
 */
class SchoolFilterEngineTest {

    private fun school(
        id: String, name: String, style: String? = null, rock: String? = null,
        lat: Double = 40.0, lon: Double = -3.0, location: String? = null
    ) = School(
        id = id, name = name, location = location, region = null, style = style,
        rockType = rock, lat = lat, lon = lon, source = null
    )

    private val albarracin = school("1", "Albarracín", style = "BOULDER", rock = "ARENISCA", lat = 40.4, lon = -1.4)
    private val zarzalejo = school("2", "Zarzalejo", style = "SPORT", rock = "GRANITO", lat = 40.5, lon = -4.1)
    private val patones = school("3", "Patones", style = "BOULDER", rock = "GNEIS", lat = 40.8, lon = -3.5)
    private val all = listOf(albarracin, zarzalejo, patones)

    @Test
    fun `texto manda sobre distancia y demas filtros`() {
        // Radio 0 km + estilo que NO es el suyo, pero el texto debe traerla igual.
        val r = SchoolFilterEngine.filter(
            all, query = "albarr", styleApiValue = "SPORT", rockTypes = listOf("GRANITO"),
            maxDistanceKm = 0.0, onlyFavorites = true, favoriteIds = emptySet(),
            userLat = 40.0, userLon = -3.0)
        assertEquals(listOf("1"), r.map { it.id })
    }

    @Test
    fun `filtro por estilo`() {
        val r = SchoolFilterEngine.filter(
            all, query = "", styleApiValue = "BOULDER", rockTypes = emptyList(),
            maxDistanceKm = null, onlyFavorites = false, favoriteIds = emptySet(),
            userLat = 40.0, userLon = -3.0)
        assertEquals(setOf("1", "3"), r.map { it.id }.toSet())
    }

    @Test
    fun `filtro por tipo de roca (varios)`() {
        val r = SchoolFilterEngine.filter(
            all, query = "", styleApiValue = null, rockTypes = listOf("GRANITO", "GNEIS"),
            maxDistanceKm = null, onlyFavorites = false, favoriteIds = emptySet(),
            userLat = 40.0, userLon = -3.0)
        assertEquals(setOf("2", "3"), r.map { it.id }.toSet())
    }

    @Test
    fun `filtro por radio deja fuera lo lejano`() {
        // Usuario en Madrid; radio corto deja fuera Albarracín (a ~130 km).
        val r = SchoolFilterEngine.filter(
            all, query = "", styleApiValue = null, rockTypes = emptyList(),
            maxDistanceKm = 60.0, onlyFavorites = false, favoriteIds = emptySet(),
            userLat = 40.4, userLon = -3.7)
        assertTrue("1" !in r.map { it.id })
        assertTrue("2" in r.map { it.id })
    }

    @Test
    fun `solo favoritos`() {
        val r = SchoolFilterEngine.filter(
            all, query = "", styleApiValue = null, rockTypes = emptyList(),
            maxDistanceKm = null, onlyFavorites = true, favoriteIds = setOf("2"),
            userLat = 40.0, userLon = -3.0)
        assertEquals(listOf("2"), r.map { it.id })
    }

    @Test
    fun `orden por distancia`() {
        // Usuario junto a Zarzalejo → primero Zarzalejo.
        val r = SchoolFilterEngine.sortByDistance(all, userLat = 40.5, userLon = -4.1)
        assertEquals("2", r.first().id)
    }

    @Test
    fun `orden por score descendente, sin score al final`() {
        val scores = mapOf("1" to 80, "3" to 95)  // 2 sin score
        val r = SchoolFilterEngine.sortByScore(all) { scores[it] ?: -1 }
        assertEquals(listOf("3", "1", "2"), r.map { it.id })
    }
}
