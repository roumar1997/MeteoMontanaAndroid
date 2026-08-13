package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.util.filterBlocksByGrade
import com.meteomontana.android.domain.util.gradeScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradeFilterTest {

    private fun line(id: String, grade: String?) = BlockLine(
        id = id, name = id, grade = grade, startType = null, linePath = null, sortOrder = 0
    )

    private fun block(id: String, vararg lines: BlockLine) = Block(
        id = id, schoolId = "s", type = "BLOCK", name = id, lat = 0.0, lon = 0.0,
        photoPath = null, description = null, createdByUid = "u", createdAt = "",
        lines = lines.toList()
    )

    @Test fun `gradeScore ordena igual que gradeArgb (mismo criterio en toda la app)`() {
        assertEquals(600, gradeScore("6A"))
        assertEquals(601, gradeScore("6A+"))
        assertEquals(711, gradeScore("7B+"))
        assertEquals(null, gradeScore("PROY"))
        assertEquals(null, gradeScore(""))
        assertNull(gradeScore(null))
    }

    @Test fun `solo la piedra con una via en rango queda marcada, la otra vias se atenuan`() {
        val alunecer = block("alunecer",
            line("l1", "7A"),      // dentro
            line("l2", "6A"))      // fuera
        val mordor = block("mordor",
            line("l3", "6B"))      // fuera
        val result = filterBlocksByGrade(listOf(alunecer, mordor), "7A", "7B")

        assertEquals(setOf("alunecer"), result.matchingBlockIds)
        assertEquals(setOf("l1"), result.matchingLineIds)
        assertEquals(3, result.totalLines)
        assertEquals(1, result.matchingLines)
    }

    @Test fun `sin minimo o sin maximo el rango queda abierto por ese lado`() {
        val b = block("b", line("l1", "3A"), line("l2", "8A+"))
        val soloMin = filterBlocksByGrade(listOf(b), "7A", null)
        assertEquals(setOf("l2"), soloMin.matchingLineIds)

        val soloMax = filterBlocksByGrade(listOf(b), null, "4A")
        assertEquals(setOf("l1"), soloMax.matchingLineIds)
    }

    @Test fun `una via con grado no reconocible (PROY) nunca entra en ningun rango`() {
        val b = block("b", line("l1", "PROY"))
        val result = filterBlocksByGrade(listOf(b), null, null)
        assertEquals(emptySet(), result.matchingLineIds)
    }
}
