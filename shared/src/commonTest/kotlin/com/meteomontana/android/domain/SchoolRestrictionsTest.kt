package com.meteomontana.android.domain

import com.meteomontana.android.domain.model.Restriction
import com.meteomontana.android.domain.model.RestrictionKind
import com.meteomontana.android.domain.model.SchoolRestrictions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolRestrictionsTest {

    private fun r(id: String, school: String = "s1", sector: String? = "sec1", blocks: List<String> = emptyList(),
                  kind: RestrictionKind = RestrictionKind.CLOSED) =
        Restriction(id, school, sector, blocks, kind, "Nidificación", "2026-01-01", "2026-07-31", true, false,
            "BOCM", null, null)

    @Test
    fun wholeSectorAffectsTheSectorAndItsBlocksButNotOthers() {
        val sr = SchoolRestrictions.of("s1", listOf(r("a")))
        assertEquals("a", sr.strongestFor("sec1", null)?.id)
        assertEquals("a", sr.strongestFor("b7", "sec1")?.id)
        assertNull(sr.strongestFor("b8", "sec2"))
    }

    @Test
    fun specificBlocksOnlyAffectThoseBlocks() {
        val sr = SchoolRestrictions.of("s1", listOf(r("a", blocks = listOf("b1", "b2"))))
        assertTrue(sr.forBlock("b1", "sec1").isNotEmpty())
        assertTrue(sr.forBlock("b3", "sec1").isEmpty())
    }

    @Test
    fun sectorChipWarnsWhenOnlySomeOfItsBlocksAreClosed() {
        fun b(id: String, type: String, sector: String? = null, name: String = id) =
            com.meteomontana.android.domain.model.Block(id, "s1", type, name, 0.0, 0.0, null, null, "u", "", emptyList(),
                sectorBlockId = sector)
        val blocks = listOf(b("sec1", "ZONE", name = "Alcores"), b("b1", "BLOCK", "sec1"), b("sec2", "ZONE"), b("b2", "BLOCK", "sec2"))
        val sr = SchoolRestrictions.of("s1", listOf(r("a", sector = null, blocks = listOf("b1"))))
        assertEquals("a", sr.strongestInSector("sec1", blocks)?.id)
        assertEquals("a", sr.strongestInSectorNamed("Alcores", blocks)?.id)
        assertNull(sr.strongestInSector("sec2", blocks))
    }

    @Test
    fun strongestWinsAndOtherSchoolsAreIgnored() {
        val sr = SchoolRestrictions.of("s1", listOf(
            r("notice", kind = RestrictionKind.NOTICE), r("closed"), r("other", school = "s2")))
        assertEquals("closed", sr.strongestFor("b1", "sec1")?.id)
        assertEquals(1, sr.closedCount)
        assertEquals(31, sr.active.first().endDay)
        assertEquals(7, sr.active.first().endMonth)
    }
}
