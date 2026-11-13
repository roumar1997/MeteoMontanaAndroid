package com.meteomontana.android.domain.usecase.notifications

import com.meteomontana.android.domain.model.Notification
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationGroupingTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val zone = TimeZone.UTC

    private fun n(id: String, type: String, at: String, actor: String? = null, target: String? = "7") =
        Notification(id, type, "t", null, "feed_post", target, null, at, actorUid = actor)

    @Test
    fun likesDelMismoPostSeFunden() {
        val g = NotificationGrouping.group(listOf(
            n("1", "FEED_LIKE", "2026-11-13T11:00:00", "a"),
            n("2", "FEED_LIKE", "2026-11-13T10:00:00", "b"),
            n("3", "FEED_LIKE", "2026-11-13T09:00:00", "c"),
            n("4", "FEED_LIKE", "2026-11-13T08:00:00", "a", target = "8")
        ), now, zone)
        assertEquals(2, g.size)
        assertEquals(2, g[0].others)
        assertEquals(listOf("1", "2", "3"), g[0].memberIds)
        assertEquals(0, g[1].others)
    }

    @Test
    fun comentariosNoSeFunden() {
        val g = NotificationGrouping.group(listOf(
            n("1", "FEED_COMMENT", "2026-11-13T11:00:00", "a"),
            n("2", "FEED_COMMENT", "2026-11-13T10:00:00", "b")
        ), now, zone)
        assertEquals(2, g.size)
    }

    @Test
    fun tramosPorDiaNatural() {
        assertEquals(NotificationSection.TODAY, NotificationGrouping.sectionOf("2026-11-13T00:30:00", now, zone))
        assertEquals(NotificationSection.THIS_WEEK, NotificationGrouping.sectionOf("2026-11-12T23:59:00", now, zone))
        assertEquals(NotificationSection.THIS_WEEK, NotificationGrouping.sectionOf("2026-11-07T10:00:00", now, zone))
        assertEquals(NotificationSection.EARLIER, NotificationGrouping.sectionOf("2026-11-06T10:00:00", now, zone))
        assertEquals(NotificationSection.EARLIER, NotificationGrouping.sectionOf("basura", now, zone))
    }
}
