package com.meteomontana.android.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.screens.schools.effectiveMaxDistanceKm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regresión: con el móvil en EE. UU. el radio de 50 km vaciaba la lista («0 escuelas») al arrancar. */
class SchoolRadiusTest {
    private fun school(lat: Double, lon: Double) =
        School(id = "$lat,$lon", name = "s", location = null, region = null, style = null, rockType = null,
            lat = lat, lon = lon, source = null)

    private val albarracin = school(40.41, -1.44)
    private val madrid = Pair(40.4168, -3.7038)

    @Test
    fun `radio con escuelas dentro se respeta`() {
        assertEquals(300.0, effectiveMaxDistanceKm(listOf(albarracin), madrid.first, madrid.second, 300.0)!!, 0.0)
    }

    @Test
    fun `usuario en EEUU, ninguna escuela cerca, el radio se ignora`() {
        assertNull(effectiveMaxDistanceKm(listOf(albarracin), 37.77, -122.42, 50.0))
    }

    @Test
    fun `sin radio sigue sin radio`() {
        assertNull(effectiveMaxDistanceKm(listOf(albarracin), madrid.first, madrid.second, null))
    }

    @Test
    fun `catalogo vacio no revienta`() {
        assertNull(effectiveMaxDistanceKm(emptyList(), 0.0, 0.0, 50.0))
    }
}
