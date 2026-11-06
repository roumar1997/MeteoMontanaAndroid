package com.meteomontana.android.notifications

import com.meteomontana.android.ui.screens.notifications.NotificationAge
import com.meteomontana.android.ui.screens.notifications.notificationAge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Regresión: la pantalla de avisos enseñaba la fecha cruda «2026-08-10». */
class NotificationAgeTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-12T12:00:00Z")

    private fun age(createdAt: String) = notificationAge(createdAt, now, utc)

    @Test
    fun `hace segundos es ahora`() = assertEquals(NotificationAge.Now, age("2026-08-12T11:59:40"))

    @Test
    fun `minutos`() = assertEquals(NotificationAge.Minutes(30), age("2026-08-12T11:30:00"))

    @Test
    fun `horas`() = assertEquals(NotificationAge.Hours(2), age("2026-08-12T10:00:00"))

    @Test
    fun `ayer por dia natural aunque hayan pasado menos de 24 h`() =
        assertEquals(NotificationAge.Yesterday, age("2026-08-11T20:00:00"))

    @Test
    fun `dias`() = assertEquals(NotificationAge.Days(3), age("2026-08-09T09:00:00"))

    @Test
    fun `mas de una semana da la fecha`() =
        assertEquals(NotificationAge.Date("2026-08-02"), age("2026-08-02T09:00:00"))

    @Test
    fun `fecha ilegible no revienta`() = assertEquals(NotificationAge.Date("basura"), age("basura"))

    @Test
    fun `el futuro cuenta como ahora`() = assertEquals(NotificationAge.Now, age("2026-08-12T12:30:00"))
}
