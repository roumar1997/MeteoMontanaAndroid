package com.meteomontana.android.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeetupGearStatusTest {
    private fun meetup(discipline: String?, partners: Int?, required: List<MeetupGear>, coverage: List<MeetupGearCoverage>) = Meetup(
        id = "m", schoolId = "s", schoolName = null, schoolLat = null, schoolLon = null, name = "n", description = null,
        discipline = discipline, privacy = "OPEN", memberLimit = null, memberCount = 1, photoUrl = null, creatorUid = "u",
        creatorUsername = null, creatorPhotoUrl = null, conversationId = "c", days = emptyList(), lastDay = "", expiresAt = 0,
        createdAt = 0, members = emptyList(), joined = false, partnersWanted = partners, requiredGear = required, gearCoverage = coverage
    )

    @Test
    fun ropeTeamWithoutRequestedGearNeedsRopeAndGrigri() {
        val lines = MeetupGearStatus.lines(meetup("ROUTE", 1, emptyList(), emptyList()))
        assertEquals(listOf(MeetupGearItem.ROPE, MeetupGearItem.GRIGRI), lines.map { it.item })
        assertTrue(lines.none { it.ok })
    }

    @Test
    fun coverageComesFromTheServer() {
        val lines = MeetupGearStatus.lines(
            meetup("ROUTE", null, listOf(MeetupGear("ROPE", 1), MeetupGear("QUICKDRAWS", 12)),
                listOf(MeetupGearCoverage("ROPE", 1, 1, true), MeetupGearCoverage("QUICKDRAWS", 12, 9, false)))
        )
        assertTrue(lines[0].ok)
        assertFalse(lines[1].ok)
        assertEquals(3, lines[1].missing)
    }

    @Test
    fun noGearRequestedMeansNoLines() {
        assertTrue(MeetupGearStatus.lines(meetup("BOULDER", null, emptyList(), emptyList())).isEmpty())
    }
}
