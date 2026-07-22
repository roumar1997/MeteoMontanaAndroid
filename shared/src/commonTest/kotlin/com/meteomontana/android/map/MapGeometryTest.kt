package com.meteomontana.android.map

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

    @Test
    fun `orden de pintado - piedras debajo, escuela encima`() {
        assertTrue(MapGeometry.paintRank("BLOCK") < MapGeometry.paintRank("PARKING"))
        assertTrue(MapGeometry.paintRank("PARKING") < MapGeometry.paintRank("ZONE"))
        assertTrue(MapGeometry.paintRank("ZONE") < MapGeometry.paintRank("SCHOOL"))
        assertEquals(MapGeometry.paintRank("block"), MapGeometry.paintRank("BLOCK")) // case-insensitive
    }
}
