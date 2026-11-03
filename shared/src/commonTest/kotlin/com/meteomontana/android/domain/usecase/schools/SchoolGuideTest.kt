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

class SchoolGuideFilterTest {
    private fun hit(id: String, block: String, sector: String?, face: String?) = LineSearchHit(
        schoolId = "s", schoolName = "S", blockId = "b-$block", blockName = block,
        lineId = id, lineName = "v$id", grade = "6A", sectorName = sector,
        photoPath = null, linePath = null, startType = null, orientation = face
    )
    private val lines = listOf(hit("1", "A", "X", "NE"), hit("2", "B", "X", "S"), hit("3", "C", "Y", null), hit("4", "D", "Y", "NO"))
    private fun f(faces: Set<String> = emptySet(), exclude: Set<String> = emptySet()) =
        LinesFilter("", emptySet(), emptySet(), LinesOrder.MOST_REPEATED, faces, exclude)

    @Test fun `cara N casa con NE y NO y las sin votar van al final aparte`() {
        val g = SchoolGuide.view(lines, f(setOf("N")), emptyMap())!!
        assertEquals(listOf("X", "Y", null), g.sectors.map { it.sector })
        assertEquals(listOf("A"), g.sectors[0].blocks.map { it.blockName })
        assertEquals(true, g.sectors.last().unknownFace)
        assertEquals(listOf("C"), g.sectors.last().blocks.map { it.blockName })
        assertEquals(2, g.sectorCount)
    }

    @Test fun `destacadas siguen los filtros y solo entran las que tienen algo que contar`() {
        val reps = listOf(
            hit("1", "A", "X", "N").copy(repetitions = 5),
            hit("2", "B", "X", "N").copy(repetitions = 9),
            hit("3", "C", "X", "N").copy(repetitions = 0, rating = 4.5, ratingCount = 2),
            hit("4", "D", "X", "S").copy(repetitions = 20)
        )
        assertEquals(listOf("2", "1"), SchoolLinesExplorer.highlights(reps, f(setOf("N")), emptyMap())!!.map { it.lineId })
        val rated = LinesFilter("", emptySet(), emptySet(), LinesOrder.BEST_RATED)
        assertEquals(listOf("3"), SchoolLinesExplorer.highlights(reps, rated, emptyMap())!!.map { it.lineId })
    }

    @Test fun `sin hacer quita las hechas y la lista usa el mismo filtro`() {
        val g = SchoolGuide.view(lines, f(exclude = setOf("1", "2")), emptyMap())!!
        assertEquals(listOf("Y"), g.sectors.map { it.sector })
        assertEquals(2, SchoolLinesExplorer.view(lines, f(exclude = setOf("1", "2")), emptyMap())!!.total)
    }
}
