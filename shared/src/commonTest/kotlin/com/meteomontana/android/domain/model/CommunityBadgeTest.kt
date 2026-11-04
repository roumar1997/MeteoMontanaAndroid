package com.meteomontana.android.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityBadgeTest {
    private fun hit(videos: List<BetaVideo> = emptyList(), reps: Int? = null, tall: Int? = null, short: Int? = null) =
        LineSearchHit(schoolId = "s", schoolName = "S", blockId = "b", blockName = "B", lineId = "l",
            lineName = "L", grade = "6a", sectorName = null, photoPath = null, linePath = null, startType = null,
            repetitions = reps, repetitionsTall = tall, repetitionsShort = short, betaVideos = videos)

    @Test fun videosByHeightThenPeople() {
        val b = hit(listOf(BetaVideo("a", "SHORT"), BetaVideo("b", "TALL"), BetaVideo("c", "TALL")), 5, 1, 0).communityBadges
        assertEquals(listOf(
            CommunityBadge.Video("TALL", listOf(BetaVideo("b", "TALL"), BetaVideo("c", "TALL"))),
            CommunityBadge.Video("SHORT", listOf(BetaVideo("a", "SHORT"))),
            CommunityBadge.Done(5), CommunityBadge.DoneByHeight("TALL", 1)), b)
    }

    @Test fun nothingWhenNobodyAndNoVideos() {
        assertEquals(emptyList(), hit(reps = 0).communityBadges)
    }
}
