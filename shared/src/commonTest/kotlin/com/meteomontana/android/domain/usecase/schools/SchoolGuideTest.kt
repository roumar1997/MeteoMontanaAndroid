package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.LineSearchHit
import kotlin.test.Test
import kotlin.test.assertEquals

class SchoolGuideTest {

    private fun hit(id: String?, block: String, sector: String?, lat: Double? = null, lon: Double? = null, name: String = "v$id") =
        LineSearchHit(
            schoolId = "s", schoolName = "S", blockId = "b-$block", blockName = block,
            lineId = id, lineName = name, grade = "6A", sectorName = sector,
            photoPath = null, linePath = null, startType = null, lat = lat, lon = lon
        )

    @Test fun `agrupa vias por piedra y piedras por sector sin piedras vacias`() {
        val s = SchoolGuide.sectors(listOf(
            hit("1", "A", "Yelmo"), hit("2", "A", "Yelmo"), hit("3", "B", "Charca"), hit(null, "C", "Yelmo")
        ))
        assertEquals(listOf("Charca", "Yelmo"), s.map { it.sector })
        assertEquals(listOf("A"), s[1].blocks.map { it.blockName })
        assertEquals(2, s[1].lineCount)
    }

    @Test fun `sector alargado de oeste a este se recorre de oeste a este`() {
        val s = SchoolGuide.sectors(listOf(
            hit("1", "Este", "X", 40.0, -3.70), hit("2", "Oeste", "X", 40.0, -3.72), hit("3", "Centro", "X", 40.0001, -3.71)
        ))
        assertEquals(listOf("Oeste", "Centro", "Este"), s[0].blocks.map { it.blockName })
    }

    @Test fun `sector alargado de norte a sur se recorre de norte a sur y sin coordenadas al final`() {
        val s = SchoolGuide.sectors(listOf(
            hit("1", "Sur", "X", 40.00, -3.7), hit("2", "Norte", "X", 40.02, -3.7), hit("3", "Suelta", "X")
        ))
        assertEquals(listOf("Norte", "Sur", "Suelta"), s[0].blocks.map { it.blockName })
    }
}
