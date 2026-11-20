package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.screens.meetups.MemberAvatar
import com.meteomontana.android.ui.screens.meetups.formatDayMonth
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Cabecera del chat de quedada, igual que la del chat de escuelas: volver, cuadradito de color con la inicial, nombre,
 * «día · N personas» (se toca y abre la quedada), avatares y los iconos de siempre (cómo llegar, invitar, silenciar).
 */
@Composable
internal fun GroupChatHeader(
    state: GroupChatUiState, onBack: () -> Unit, onOpenMeetup: (String) -> Unit,
    onDirections: () -> Unit, onInvite: (String) -> Unit, onToggleMute: () -> Unit
) {
    val tint = if (state.isGym) listOf(Color(0xFF3B6FD6), Color(0xFF7A45C4)) else listOf(Color(0xFF1FA84E), Color(0xFF1F7F8C))
    val people = state.members.size.takeIf { it > 0 } ?: (state.memberNames.size + 1)
    val subtitle = listOfNotNull(state.firstDay?.let { formatDayMonth(it) }, androidx.compose.ui.res.pluralStringResource(R.plurals.chat_header_people, people, people)).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.common_back)) }
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(tint)),
            contentAlignment = Alignment.Center
        ) { Text(state.name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Column(
            Modifier.weight(1f).padding(horizontal = 10.dp).let { m -> state.meetupId?.let { id -> m.clickable { onOpenMeetup(id) } } ?: m }
        ) {
            Text(state.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (state.members.isNotEmpty()) Box(Modifier.padding(end = 4.dp)) {
            state.members.take(3).forEachIndexed { i, m ->
                Box(Modifier.padding(start = (i * 16).dp).size(26.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                    MemberAvatar(m.photoUrl, 26.dp)
                }
            }
            Box(Modifier.size((26 + (state.members.take(3).size - 1) * 16).dp, 26.dp))
        }
        if (state.schoolLat != null && state.schoolLon != null) IconButton(onClick = onDirections) {
            Icon(Icons.Outlined.Directions, stringResource(R.string.group_chat_screen_v3_como_llegar), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.meetupId?.let { id ->
            IconButton(onClick = { onInvite(id) }) {
                Icon(Icons.Outlined.PersonAdd, stringResource(R.string.group_chat_screen_v4_invitar_al_grupo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onToggleMute) {
            Icon(
                if (state.muted) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                if (state.muted) stringResource(R.string.w_unmute) else stringResource(R.string.w_mute),
                tint = if (state.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** Línea fija con el material del grupo; se toca para abrir «¿Qué llevas tú?». */
@Composable
internal fun GroupChatGearRow(summary: String?, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Luggage, null, tint = terraFillColor(), modifier = Modifier.size(18.dp))
        Text(
            summary ?: stringResource(R.string.group_chat_screen_v2_sin_material_indicado), fontSize = 13.sp, maxLines = 1,
            fontWeight = if (summary != null) FontWeight.Medium else FontWeight.Normal, overflow = TextOverflow.Ellipsis,
            color = if (summary != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(stringResource(R.string.meetup_detail_my_gear) + " ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = terraFillColor())
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
