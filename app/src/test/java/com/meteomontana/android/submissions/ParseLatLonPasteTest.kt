package com.meteomontana.android.submissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests para el parser de coordenadas pegadas (texto de Google Maps).
 *
 * La función está duplicada en `SubmitSchoolScreen.kt` y `EditBlockDialog.kt`
 * como `private fun parseLatLonPaste`. Tras la Fase 1 del refactor se moverá
 * a `domain/util/` y estos tests apuntarán ahí.
 *
 * Mientras tanto, replicamos aquí para tener red de seguridad.
 */
class ParseLatLonPasteTest {

    /** Replica de la función original — debe coincidir 1:1 con el código de producción. */
    private fun parseLatLonPaste(raw: String): Pair<Double, Double>? {
        val matches = Regex("-?\\d+[\\.,]?\\d*").findAll(raw).map { it.value }.toList()
        if (matches.size < 2) return null
        val lat = matches[0].replace(",", ".").toDoubleOrNull() ?: return null
        val lon = matches[1].replace(",", ".").toDoubleOrNull() ?: return null
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return lat to lon
    }

    @Test fun `formato Google Maps con coma espacio`() {
        val r = parseLatLonPaste("40.4168, -3.7038")
        assertNotNull(r)
        assertEquals(40.4168, r!!.first, 0.0001)
        assertEquals(-3.7038, r.second, 0.0001)
    }

    @Test fun `formato sin espacio`() {
        val r = parseLatLonPaste("40.4168,-3.7038")
        assertNotNull(r)
        assertEquals(40.4168, r!!.first, 0.0001)
        assertEquals(-3.7038, r.second, 0.0001)
    }

    @Test fun `formato con espacio y sin coma`() {
        val r = parseLatLonPaste("40.4168 -3.7038")
        assertNotNull(r)
        assertEquals(40.4168, r!!.first, 0.0001)
        assertEquals(-3.7038, r.second, 0.0001)
    }

    @Test fun `formato espanol con coma decimal funciona`() {
        // El regex `-?\d+[\.,]?\d*` acepta tanto punto como coma decimal.
        // Esto permite pegar coordenadas con formato español ("40,4168 -3,7038")
        // sin tener que normalizar primero.
        val r = parseLatLonPaste("40,4168 -3,7038")
        assertNotNull(r)
        assertEquals(40.4168, r!!.first, 0.0001)
        assertEquals(-3.7038, r.second, 0.0001)
    }

    @Test fun `texto sin coordenadas devuelve null`() {
        assertNull(parseLatLonPaste("hola mundo"))
        assertNull(parseLatLonPaste(""))
        assertNull(parseLatLonPaste("solo-un-numero 42"))
    }

    @Test fun `un solo numero devuelve null`() {
        // Tiene un número pero no dos.
        assertNull(parseLatLonPaste("40.4168"))
    }

    @Test fun `coordenadas fuera de rango devuelve null`() {
        // lat > 90
        assertNull(parseLatLonPaste("91.0, 0.0"))
        // lat < -90
        assertNull(parseLatLonPaste("-91.0, 0.0"))
        // lon > 180
        assertNull(parseLatLonPaste("0.0, 181.0"))
        // lon < -180
        assertNull(parseLatLonPaste("0.0, -181.0"))
    }

    @Test fun `cerca de Madrid funciona`() {
        val r = parseLatLonPaste("40.4168, -3.7038")
        assertNotNull(r)
    }

    @Test fun `Polo Norte y Sur en rango limite funcionan`() {
        val n = parseLatLonPaste("90.0, 0.0")
        val s = parseLatLonPaste("-90.0, 0.0")
        assertNotNull(n)
        assertNotNull(s)
        assertEquals(90.0, n!!.first, 0.0001)
        assertEquals(-90.0, s!!.first, 0.0001)
    }

    @Test fun `coordenadas enteras sin decimales`() {
        val r = parseLatLonPaste("40, -3")
        assertNotNull(r)
        assertEquals(40.0, r!!.first, 0.0001)
        assertEquals(-3.0, r.second, 0.0001)
    }

    @Test fun `texto con coordenadas embebidas extrae las dos primeras`() {
        // Google Maps a veces incluye texto antes/después.
        val r = parseLatLonPaste("Mi ubicación: 40.4168, -3.7038 (Madrid)")
        assertNotNull(r)
        assertEquals(40.4168, r!!.first, 0.0001)
        assertEquals(-3.7038, r.second, 0.0001)
    }

    @Test fun `coordenadas negativas se capturan correctamente`() {
        val r = parseLatLonPaste("-23.5505, -46.6333") // São Paulo
        assertNotNull(r)
        assertEquals(-23.5505, r!!.first, 0.0001)
        assertEquals(-46.6333, r.second, 0.0001)
    }
}
