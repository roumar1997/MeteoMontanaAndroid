package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.util.availableGrades
import com.meteomontana.android.domain.util.filterBlocksByGrades
import com.meteomontana.android.domain.util.gradeScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradeFilterTest {

    private fun line(id: String, grade: String?, name: String = id) = BlockLine(
        id = id, name = name, grade = grade, startType = null, linePath = null, sortOrder = 0
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

    @Test fun `availableGrades solo lista los grados que existen de verdad, de dificil a facil`() {
        val b = block("b", line("l1", "6A"), line("l2", "7B+"), line("l3", "PROY"), line("l4", "6A"))
        assertEquals(listOf("7B+", "6A"), availableGrades(listOf(b)))
    }

    @Test fun `solo la piedra con una via en la seleccion queda marcada, agrupada por grado`() {
        val alunecer = block("alunecer",
            line("l1", "7A", "Via A"),      // seleccionada
            line("l2", "6A", "Via B"))      // no seleccionada
        val mordor = block("mordor",
            line("l3", "6B", "Via C"))      // no seleccionada
        val result = filterBlocksByGrades(listOf(alunecer, mordor), setOf("7A"))

        assertEquals(setOf("alunecer"), result.matchingBlockIds)
        assertEquals(setOf("l1"), result.matchingLineIds)
        assertEquals(3, result.totalLines)
        assertEquals(1, result.matchingLines)
        assertEquals(listOf("7A"), result.groups.map { it.first })
        assertEquals("Via A", result.groups.single().second.single().lineName)
    }

    @Test fun `varios grados seleccionados agrupan cada uno por separado, de dificil a facil`() {
        val b = block("b", line("l1", "6A"), line("l2", "8A+"), line("l3", "6A"))
        val result = filterBlocksByGrades(listOf(b), setOf("6A", "8A+"))
        assertEquals(listOf("8A+", "6A"), result.groups.map { it.first })
        assertEquals(2, result.groups.last().second.size)
    }

    @Test fun `una via con grado no reconocible (PROY) nunca entra en la seleccion`() {
        val b = block("b", line("l1", "PROY"))
        val result = filterBlocksByGrades(listOf(b), emptySet())
        assertEquals(emptySet(), result.matchingLineIds)
    }
}
