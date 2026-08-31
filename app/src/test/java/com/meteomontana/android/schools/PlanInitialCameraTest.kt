package com.meteomontana.android.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.screens.schools.planInitialCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la cámara inicial del mapa de escuelas (función pura, sin MapLibre).
 * Cubre el arreglo M3: al abrir el mapa debe ENCUADRAR las escuelas visibles
 * (igual que tras filtrar), no abrir a un zoom fijo centrado en el usuario.
 */
class PlanInitialCameraTest {

    private fun school(id: String, lat: Double, lon: Double) = School(
        id = id, name = id, location = null, region = null,
        style = null, rockType = null, lat = lat, lon = lon, source = null
    )

    @Test fun `varias escuelas encuadra las visibles`() {
        val schools = listOf(school("A", 40.4, -3.7), school("B", 40.8, -3.9))
        val plan = planInitialCamera(schools, userLat = 40.0, userLon = -3.0)
        assertTrue("con 2+ escuelas debe encuadrar (fit)", plan.fit)
    }

    @Test fun `una escuela centra en ella con zoom cercano`() {
        val plan = planInitialCamera(listOf(school("A", 40.4, -3.7)), userLat = 10.0, userLon = 10.0)
        assertFalse(plan.fit)
        assertEquals(40.4, plan.lat, 1e-9)
        assertEquals(-3.7, plan.lon, 1e-9)
        assertEquals(13.5, plan.zoom, 1e-9)
    }

    @Test fun `sin escuelas con ubicacion centra en el usuario`() {
        val plan = planInitialCamera(emptyList(), userLat = 41.0, userLon = 2.0)
        assertFalse(plan.fit)
        assertEquals(41.0, plan.lat, 1e-9)
        assertEquals(2.0, plan.lon, 1e-9)
        assertEquals(8.0, plan.zoom, 1e-9)
    }

    @Test fun `sin escuelas ni ubicacion muestra Espana`() {
        val plan = planInitialCamera(emptyList(), userLat = null, userLon = null)
        assertFalse(plan.fit)
        assertEquals(40.4, plan.lat, 1e-9)
        assertEquals(-3.7, plan.lon, 1e-9)
        assertEquals(5.0, plan.zoom, 1e-9)
    }
}
