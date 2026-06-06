package com.meteomontana.android.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests para la fórmula de Haversine.
 *
 * La función está duplicada en `SchoolListViewModel` y `SchoolsMapPanel`.
 * Tras la Fase 1 del refactor pasará a `domain/util/Geo.kt` y estos tests
 * apuntarán ahí.
 *
 * Mientras tanto, replicamos la fórmula aquí en formato puro para que el
 * test exista YA como red de seguridad para el refactor.
 */
class HaversineTest {

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    @Test fun `distancia entre el mismo punto es cero`() {
        val d = haversineKm(40.4168, -3.7038, 40.4168, -3.7038)
        assertEquals(0.0, d, 0.0001)
    }

    @Test fun `Madrid a Barcelona aprox 504 km`() {
        // Coords aproximadas: Madrid (40.4168, -3.7038), Barcelona (41.3851, 2.1734)
        val d = haversineKm(40.4168, -3.7038, 41.3851, 2.1734)
        // valor real ~504 km, tolerancia ±5 km
        assertEquals(504.0, d, 5.0)
    }

    @Test fun `Madrid a La Pedriza aprox 50 km`() {
        // La Pedriza ≈ 40.768, -3.852
        val d = haversineKm(40.4168, -3.7038, 40.768, -3.852)
        // valor real ~40-42 km, tolerancia ±3 km
        assertEquals(41.0, d, 3.0)
    }

    @Test fun `Madrid a Albarracin aprox 220 km`() {
        // Albarracín ≈ 40.408, -1.444
        val d = haversineKm(40.4168, -3.7038, 40.408, -1.444)
        // valor real ~191 km, tolerancia ±5 km
        assertEquals(191.0, d, 5.0)
    }

    @Test fun `distancia es simetrica`() {
        val a = haversineKm(40.4168, -3.7038, 41.3851, 2.1734)
        val b = haversineKm(41.3851, 2.1734, 40.4168, -3.7038)
        assertEquals(a, b, 0.0001)
    }

    @Test fun `distancia es positiva en hemisferios opuestos`() {
        val d = haversineKm(40.0, 0.0, -40.0, 0.0)
        // ~8900 km (dos veces ~4450 km de un cuadrante)
        assertEquals(8895.0, d, 50.0)
    }

    @Test fun `puntos antipodales aprox la mitad de la circunferencia`() {
        // mitad de la Tierra: ~20015 km (πR)
        val d = haversineKm(0.0, 0.0, 0.0, 180.0)
        assertEquals(20015.0, d, 50.0)
    }
}
