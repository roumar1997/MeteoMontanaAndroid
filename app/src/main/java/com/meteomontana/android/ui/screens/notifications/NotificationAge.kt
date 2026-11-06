package com.meteomontana.android.ui.screens.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.screens.profile.shortJournalDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Antigüedad de una notificación, sin texto: la traducción la pone la pantalla. */
internal sealed interface NotificationAge {
    data object Now : NotificationAge
    data class Minutes(val n: Long) : NotificationAge
    data class Hours(val n: Long) : NotificationAge
    data object Yesterday : NotificationAge
    data class Days(val n: Long) : NotificationAge

    /** Más de una semana (o fecha ilegible): se enseña la fecha corta. */
    data class Date(val iso: String) : NotificationAge
}

/**
 * «2026-08-10T09:15:00» (hora del servidor = UTC) → antigüedad.
 * «Ayer» se decide por día natural en la zona del móvil, no por 24 h.
 */
internal fun notificationAge(
    createdAt: String,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): NotificationAge {
    val created = runCatching {
        LocalDateTime.parse(createdAt.take(19)).toInstant(ZoneOffset.UTC)
    }.getOrNull() ?: return NotificationAge.Date(createdAt.take(10))
    val minutes = Duration.between(created, now).toMinutes().coerceAtLeast(0)
    val dayGap = ChronoUnit.DAYS.between(
        created.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()
    )
    return when {
        minutes < 1 -> NotificationAge.Now
        minutes < 60 -> NotificationAge.Minutes(minutes)
        dayGap <= 0L -> NotificationAge.Hours(minutes / 60)
        dayGap == 1L -> NotificationAge.Yesterday
        dayGap < 7L -> NotificationAge.Days(dayGap)
        else -> NotificationAge.Date(created.atZone(zone).toLocalDate().toString())
    }
}

@Composable
internal fun notificationDateLabel(createdAt: String): String {
    val age = remember(createdAt) { notificationAge(createdAt) }
    return when (age) {
        NotificationAge.Now -> stringResource(R.string.feed_time_now)
        is NotificationAge.Minutes -> stringResource(R.string.feed_time_min, age.n)
        is NotificationAge.Hours -> stringResource(R.string.feed_time_hours, age.n)
        NotificationAge.Yesterday -> stringResource(R.string.chat_day_yesterday)
        is NotificationAge.Days -> stringResource(R.string.feed_time_days, age.n)
        is NotificationAge.Date -> shortJournalDate(age.iso)
    }
}
