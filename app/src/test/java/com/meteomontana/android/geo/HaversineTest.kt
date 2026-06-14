package com.meteomontana.android.geo

import com.meteomontana.android.domain.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la fórmula de Haversine. Ahora apuntan a la implementación
 * compartida `domain/util/Geo.kt` (KMP `commonMain`, reutilizable por iOS),
 * que unificó las 3 copias que había en `SchoolListViewModel`,
 * `SchoolsMapPanel` y `FavoritesWidget`. Eran la red de seguridad de ese
 * refactor: si `Geo` se desviara, estos tests se ponen rojos.
 */
class HaversineTest {

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        Geo.haversineKm(lat1, lon1, lat2, lon2)

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
