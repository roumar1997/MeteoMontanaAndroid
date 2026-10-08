package com.meteomontana.android.domain.usecase.assistant

import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.model.WeekendAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantActionsTest {

    private fun hit(lineId: String?, name: String?, school: String = "s1", grade: String? = "6B") = LineSearchHit(
        schoolId = school, schoolName = "Escuela", blockId = "b", blockName = "Piedra", lineId = lineId,
        lineName = name, grade = grade, sectorName = "Techos", photoPath = null, linePath = null,
        startType = null, discipline = "BOULDER"
    )

    @Test fun unNombreExactoGanaAlQueSoloLoContiene() {
        val hits = listOf(hit("1", "La lágrima"), hit("2", "La lágrima del sur"), hit(null, "La lágrima (piedra)"))

        val r = AssistantActions.matchLines(hits, "la lagrima", null)

        assertEquals(listOf("1"), r.map { it.lineId })      // sin tildes ni mayúsculas, y solo la exacta
    }

    @Test fun sinNombreExactoSalenLasQueLoContienenYMaximoTres() {
        val hits = (1..5).map { hit("$it", "Sur $it") }

        assertEquals(3, AssistantActions.matchLines(hits, "sur", null).size)
        assertTrue(AssistantActions.matchLines(hits, "norte", null).isEmpty())
        assertTrue(AssistantActions.matchLines(hits, "   ", null).isEmpty())
    }

    @Test fun laEscuelaDichaDescartaLasHomonimas() {
        val hits = listOf(hit("1", "Sur", school = "a"), hit("2", "Sur", school = "b"))
        assertEquals(listOf("2"), AssistantActions.matchLines(hits, "sur", "b").map { it.lineId })
    }

    @Test fun laEntradaDeDiarioLlevaLoDeLaVia() {
        val req = AssistantActions.journalRequest(hit("9", "La lágrima"), "PROJECT", "2026-10-08")
        assertEquals("La lágrima", req.blockName)
        assertEquals("9", req.lineId)
        assertEquals("PROJECT", req.status)
        assertEquals("2026-10-08", req.date)
        assertEquals("BOULDER", req.discipline)
        assertEquals("6B", req.grade)
        assertEquals("s1|#9", AssistantActions.doneKey(hit("9", "x")))
    }

    private fun entry(lineId: String?, name: String, status: String) = JournalSession(
        id = name, schoolId = "s1", schoolName = null, sector = null, blockName = name, grade = null, notes = null,
        date = "2026-01-01", createdAt = "2026-01-01", lineId = lineId, status = status
    )

    @Test fun noSeDuplicaLoYaApuntadoConElMismoEstado() {
        val h = hit("9", "La lágrima")
        assertTrue(AssistantActions.isLogged(listOf(entry("9", "x", "DONE")), h, "DONE"))
        assertFalse(AssistantActions.isLogged(listOf(entry("9", "x", "PROJECT")), h, "DONE"))     // proyecto ≠ hecha
        assertTrue(AssistantActions.isLogged(listOf(entry(null, "LA LÁGRIMA".lowercase(), "DONE")), h, "DONE"))
        assertFalse(AssistantActions.isLogged(emptyList(), h, "DONE"))
    }

    private fun alert(enabled: Boolean, mode: String = "SCHOOLS", ids: List<String> = emptyList()) =
        WeekendAlert(enabled = enabled, notifyDay = 4, notifyHour = 18, schoolIds = ids, mode = mode)

    @Test fun activarElAvisoAnadeLaEscuelaYConservaDiaYHora() {
        val c = AssistantActions.withSchoolAlert(alert(false), "a")
        val a = assertNotNull(c.alert)
        assertTrue(a.enabled)
        assertEquals(listOf("a"), a.schoolIds)
        assertEquals(4, a.notifyDay)
        assertEquals(18, a.notifyHour)
    }

    @Test fun siYaEstabaNoCambiaNadaYConTresNoCabe() {
        assertTrue(AssistantActions.withSchoolAlert(alert(true, ids = listOf("a")), "a").already)
        val full = AssistantActions.withSchoolAlert(alert(true, ids = listOf("a", "b", "c")), "d")
        assertTrue(full.full)
        assertNull(full.alert)
    }

    @Test fun desdeElModoCercaDeMiPasaAEscuelasElegidasSoloConEsta() {
        val c = AssistantActions.withSchoolAlert(alert(true, mode = "NEARBY", ids = listOf("x", "y")), "a")
        val a = assertNotNull(c.alert)
        assertEquals("SCHOOLS", a.mode)
        assertEquals(listOf("a"), a.schoolIds)
    }
}
