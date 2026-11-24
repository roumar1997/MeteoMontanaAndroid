package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/** "hace 2 h" a partir de un createdAt "yyyy-MM-ddTHH:mm:ss" (hora del servidor,
 *  que es UTC — interpretarla como local sumaba 2h de error en España). */
@Composable
internal fun relativeTime(createdAt: String): String {
    val minutes = remember(createdAt) {
        runCatching {
            val t = java.time.LocalDateTime.parse(createdAt.take(19))
                .toInstant(java.time.ZoneOffset.UTC)
            java.time.Duration.between(t, java.time.Instant.now()).toMinutes()
                .coerceAtLeast(0)
        }.getOrDefault(0L)
    }
    return when {
        minutes < 1 -> stringResource(R.string.feed_time_now)
        minutes < 60 -> stringResource(R.string.feed_time_min, minutes)
        minutes < 60 * 24 -> stringResource(R.string.feed_time_hours, minutes / 60)
        else -> stringResource(R.string.feed_time_days, minutes / (60 * 24))
    }
}
