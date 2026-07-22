package com.meteomontana.android.map

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.usecase.map.GeoPoint
import com.meteomontana.android.domain.usecase.map.MapGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Red de seguridad del parseo del muro y el orden de pintado del mapa. */
class MapGeometryTest {

    @Test
    fun `parsea un trazado valido`() {
        val pts = MapGeometry.parseWallPath("[[40.1,-3.2],[40.2,-3.3],[40.3,-3.4]]")
        assertEquals(3, pts.size)
        assertEquals(40.1, pts[0].lat)
        assertEquals(-3.4, pts[2].lon)
    }

    @Test
    fun `nulo o blanco o basura devuelve vacio sin lanzar`() {
        assertTrue(MapGeometry.parseWallPath(null).isEmpty())
        assertTrue(MapGeometry.parseWallPath("").isEmpty())
        assertTrue(MapGeometry.parseWallPath("no soy json").isEmpty())
        assertTrue(MapGeometry.parseWallPath("[[40.1]]").isEmpty()) // par incompleto
    }

    @Test
    fun `punto medio de un muro`() {
        // 3 puntos → índice 1 (el del medio).
        val mid = MapGeometry.wallMidpoint("[[0.0,0.0],[1.0,1.0],[2.0,2.0]]")
        assertEquals(1.0, mid?.lat)
        assertEquals(1.0, mid?.lon)
    }

    @Test
    fun `sin trazado no hay punto medio`() {
        assertNull(MapGeometry.wallMidpoint(null))
        assertNull(MapGeometry.wallMidpoint("[[1.0,1.0]]")) // solo 1 punto
    }

    private fun block(id: String, lat: Double, lon: Double) = Block(
        id = id, schoolId = "s", type = "BLOCK", name = id, lat = lat, lon = lon,
        photoPath = null, description = null, createdByUid = "", createdAt = "", lines = emptyList()
    )

    @Test
    fun `centroide de puntos`() {
        val c = MapGeometry.centroid(listOf(GeoPoint(0.0, 0.0), GeoPoint(2.0, 4.0)))
        assertEquals(1.0, c?.lat)
        assertEquals(2.0, c?.lon)
    }

    @Test
    fun `centroide de lista vacia es null`() {
        assertNull(MapGeometry.centroid(emptyList()))
    }

    @Test
    fun `bloques dentro de radio excluyen lejanos y el propio`() {
        val parking = block("P", 40.4, -3.7)
        val cerca = block("cerca", 40.401, -3.701)   // ~150 m
        val lejos = block("lejos", 41.0, -3.7)        // ~66 km
        val r = MapGeometry.blocksWithinKm(
            listOf(parking, cerca, lejos), lat = 40.4, lon = -3.7, km = 0.8, excludeId = "P")
        assertEquals(listOf("cerca"), r.map { it.id })
    }

    @Test
    fun `orden de pintado - piedras debajo, escuela encima`() {
        assertTrue(MapGeometry.paintRank("BLOCK") < MapGeometry.paintRank("PARKING"))
        assertTrue(MapGeometry.paintRank("PARKING") < MapGeometry.paintRank("ZONE"))
        assertTrue(MapGeometry.paintRank("ZONE") < MapGeometry.paintRank("SCHOOL"))
        assertEquals(MapGeometry.paintRank("block"), MapGeometry.paintRank("BLOCK")) // case-insensitive
    }
}
