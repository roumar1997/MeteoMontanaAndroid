package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.LineSearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExploreSectorsTest {
    private fun hit(id: String, sector: String?, discipline: String? = null) = LineSearchHit(
        schoolId = "z", schoolName = "Zarzalejo", blockId = "b$id", blockName = "Piedra $id", lineId = "l$id", lineName = "Via $id",
        grade = "6a", sectorName = sector, photoPath = null, linePath = null, startType = null, discipline = discipline
    )

    @Test fun splitsBySectorKeepingOrder() {
        val out = ExploreSectors.split(listOf(hit("1", "Mundo al Revés"), hit("2", "Dos"), hit("3", "Mundo al Revés"), hit("4", null)))!!
        assertEquals(listOf<String?>("Mundo al Revés", "Dos", null), out.map { it.name })
        assertEquals(2, out[0].hits.size)
    }

    @Test fun singleSectorIsNotSplit() {
        assertNull(ExploreSectors.split(listOf(hit("1", "A"), hit("2", "A"))))
        assertNull(ExploreSectors.split(listOf(hit("1", null), hit("2", " "))))
    }

    @Test fun allBouldersUsesOwnDisciplineThenSchoolStyle() {
        assertTrue(ExploreSectors.allBoulders(listOf(hit("1", "A", "BOULDER")), null))
        assertFalse(ExploreSectors.allBoulders(listOf(hit("1", "A", "BOULDER"), hit("2", "A", "ROUTE")), "Bloque"))
        assertTrue(ExploreSectors.allBoulders(listOf(hit("1", "A")), "Bloque"))
        assertFalse(ExploreSectors.allBoulders(listOf(hit("1", "A")), "Bloque, Vía"))
        assertTrue(ExploreSectors.allRoutes(listOf(hit("1", "A", "ROUTE")), null))
    }
}
