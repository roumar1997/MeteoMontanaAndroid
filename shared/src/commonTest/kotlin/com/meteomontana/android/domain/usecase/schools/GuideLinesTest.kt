package com.meteomontana.android.domain.usecase.schools

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.LineSearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuideLinesTest {

    private fun line(id: String, order: Int, desc: String? = null) = BlockLine(
        id = id, name = "v$id", grade = "6A", startType = "SIT", linePath = "[[0,0],[1,1]]",
        sortOrder = order, photoPath = "foto.jpg", lineDescription = desc
    )

    private fun block(id: String, type: String, name: String, lines: List<BlockLine> = emptyList(), sector: String? = null) =
        Block(
            id = id, schoolId = "s", type = type, name = name, lat = 40.0, lon = -3.0, photoPath = null,
            description = null, createdByUid = "u", createdAt = "", lines = lines, sectorBlockId = sector
        )

    private val blocks = listOf(
        block("z1", "ZONE", "Sector Norte"),
        block("p1", "PARKING", "Parking"),
        block("b1", "BLOCK", "Patatonio", listOf(line("2", 1), line("1", 0, "  Salida por la izquierda ")), sector = "z1"),
        block("b2", "BLOCK", "Suelta", listOf(line("3", 0, "   ")))
    )

    @Test fun `sin conexion la guia sale de las piedras guardadas`() {
        val hits = GuideLines.fromBlocks("s", "Escuela", blocks)
        assertEquals(listOf("1", "2", "3"), hits.map { it.lineId })
        assertEquals("Sector Norte", hits[0].sectorName)
        assertNull(hits[2].sectorName)
        assertEquals("Salida por la izquierda", hits[0].lineDescription)
        assertNull(hits[2].lineDescription, "un comentario en blanco no cuenta")
        assertEquals("SIT", hits[0].startType)
        assertEquals("foto.jpg", hits[0].photoPath)
    }

    @Test fun `con conexion se anade el comentario a lo que manda el buscador`() {
        val fromServer = listOf(
            LineSearchHit("s", "Escuela", "b1", "Patatonio", "1", "v1", "6A", null, null, null, null, repetitions = 7),
            LineSearchHit("s", "Escuela", "b1", "Patatonio", "2", "v2", "6A", null, null, null, null)
        )
        val out = GuideLines.withDescriptions(fromServer, blocks)
        assertEquals("Salida por la izquierda", out[0].lineDescription)
        assertEquals(7, out[0].repetitions)
        assertNull(out[1].lineDescription)
    }
}
