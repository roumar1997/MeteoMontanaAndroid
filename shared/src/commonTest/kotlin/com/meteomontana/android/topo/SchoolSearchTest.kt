package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.util.SchoolSearchKind
import com.meteomontana.android.domain.util.searchSchool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchoolSearchTest {
    private fun line(id: String, name: String, grade: String?) = BlockLine(
        id = id, name = name, grade = grade, startType = null, linePath = null, sortOrder = 0
    )

    private fun block(id: String, type: String, name: String, discipline: String = "BOULDER", vararg lines: BlockLine) = Block(
        id = id, schoolId = "s", type = type, name = name, lat = 0.0, lon = 0.0,
        photoPath = null, description = null, createdByUid = "u", createdAt = "",
        lines = lines.toList(), discipline = discipline
    )

    private val blocks = listOf(
        block("z", "ZONE", "Sector Norte"),
        block("p", "PARKING", "Parking Norte"),
        block("b1", "BLOCK", "Piedra Norte", "BOULDER", line("l1", "Norteña", "6A")),
        block("b2", "BLOCK", "Muro", "ROUTE", line("l2", "Fisura norte", "7A+"))
    )

    @Test fun `una consulta corta no busca`() {
        assertTrue(searchSchool(blocks, "n").isEmpty())
    }

    @Test fun `sectores y parkings salen primero y llevan su tipo`() {
        val hits = searchSchool(blocks, "norte")
        assertEquals(listOf(SchoolSearchKind.SECTOR, SchoolSearchKind.PARKING), hits.take(2).map { it.kind })
        assertEquals(listOf("z", "p"), hits.take(2).map { it.blockId })
    }

    @Test fun `las vias llevan grado y tipo segun la disciplina de su piedra`() {
        val hits = searchSchool(blocks, "fisura")
        assertEquals(1, hits.size)
        assertEquals("Fisura norte", hits[0].label)
        assertEquals("7A+", hits[0].grade)
        assertEquals(SchoolSearchKind.ROUTE, hits[0].kind)
        assertEquals("Muro", hits[0].parentName)
    }

    @Test fun `una piedra coincidente cuenta sus vias y respeta el limite`() {
        val hits = searchSchool(blocks, "piedra")
        assertEquals(1, hits[0].lineCount)
        assertEquals(2, searchSchool(blocks, "norte", limit = 2).size)
    }
}
