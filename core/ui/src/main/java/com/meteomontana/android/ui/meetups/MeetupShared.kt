package com.meteomontana.android.ui.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.meteomontana.android.core.ui.R
import com.meteomontana.android.domain.model.MeetupGearItem
import com.meteomontana.android.util.CalendarLabels

/** Piezas de quedadas que también usan los chats (avatar, material y fechas). */
@Composable
fun MemberAvatar(photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f))
        }
    }
}

fun gearLabelRes(item: String): Int = when (item) {
    MeetupGearItem.ROPE -> R.string.gear_rope
    MeetupGearItem.GRIGRI -> R.string.gear_grigri
    MeetupGearItem.QUICKDRAWS -> R.string.gear_quickdraws
    else -> R.string.gear_crashpad
}

fun formatDayMonth(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    return "$day ${monthNames().getOrElse(month - 1) { "?" }}"
}

fun monthNames() = CalendarLabels.monthsShort().map { it.lowercase() }
