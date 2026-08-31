package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los sectores ofrecidos desde la ficha de un parking.
 *
 * Lo que se fija: que ordenen por lo que queda POR ANDAR desde ese parking (no
 * por nombre ni por distancia al usuario) y que cuenten bien sus piedras.
 */
class SectoresDesdeParkingTest {

    private fun bloque(id: String, tipo: String, lat: Double, lon: Double,
                       sector: String? = null, nombre: String = id) = Block(
        id = id, schoolId = "e", type = tipo, name = nombre, lat = lat, lon = lon,
        photoPath = null, description = null, createdByUid = "u", createdAt = "",
        lines = emptyList(), sectorBlockId = sector
    )

    // ~1,1 km por cada 0,01 grados de latitud.
    private val parking = bloque("p", "PARKING", 40.0000, -4.0)

    @Test
    fun `ordena por cercania al parking, no por nombre`() {
        val todos = listOf(
            parking,
            bloque("lejos", "ZONE", 40.0200, -4.0, nombre = "A lejano"),
            bloque("cerca", "ZONE", 40.0010, -4.0, nombre = "Z cercano")
        )

        val out = SectoresDesdeParking.calcular(parking, todos)

        assertEquals(listOf("cerca", "lejos"), out.map { it.sector.id })
    }

    @Test
    fun `cuenta las piedras de cada sector`() {
        val todos = listOf(
            parking,
            bloque("s1", "ZONE", 40.001, -4.0),
            bloque("b1", "BLOCK", 40.001, -4.0, sector = "s1"),
            bloque("b2", "BLOCK", 40.001, -4.0, sector = "s1"),
            bloque("b3", "BLOCK", 40.001, -4.0, sector = "otro")
        )

        assertEquals(2, SectoresDesdeParking.calcular(parking, todos).first().piedras)
    }

    @Test
    fun `solo devuelve sectores — ni parkings ni piedras`() {
        val todos = listOf(
            parking,
            bloque("p2", "PARKING", 40.001, -4.0),
            bloque("b", "BLOCK", 40.001, -4.0),
            bloque("s", "ZONE", 40.001, -4.0)
        )

        assertEquals(listOf("s"), SectoresDesdeParking.calcular(parking, todos).map { it.sector.id })
    }

    @Test
    fun `una escuela sin sectores no ofrece nada`() {
        val todos = listOf(parking, bloque("b", "BLOCK", 40.001, -4.0))

        assertTrue(SectoresDesdeParking.calcular(parking, todos).isEmpty())
    }

    @Test
    fun `la distancia se lee en metros de cerca y en km de lejos`() {
        val cerca = SectorCercano(bloque("s", "ZONE", 0.0, 0.0), metros = 350, piedras = 0)
        val lejos = SectorCercano(bloque("s", "ZONE", 0.0, 0.0), metros = 1234, piedras = 0)

        assertEquals("350 m", cerca.distanciaTexto)
        assertEquals("1,2 km", lejos.distanciaTexto)
    }
}
