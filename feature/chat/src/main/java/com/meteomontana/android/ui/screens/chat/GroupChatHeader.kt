package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.feature.chat.R
import com.meteomontana.android.domain.model.MeetupGearLine
import com.meteomontana.android.ui.meetups.formatDayMonth
import com.meteomontana.android.ui.meetups.gearLabelRes
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Cabecera del chat de quedada: la [ChatTopBar] de todos los chats (volver, cuadradito de color con la inicial, nombre,
 * «día · N personas», campana). Lo demas (cómo llegar, invitar, ver gente, material) va debajo, en [GroupChatActions] y
 * [GroupChatGearCard].
 */
@Composable
fun GroupChatHeader(
    state: GroupChatUiState, onBack: () -> Unit, onOpenMeetup: (String) -> Unit, onToggleMute: () -> Unit
) {
    val tint = if (state.isGym) listOf(Color(0xFF3B6FD6), Color(0xFF7A45C4)) else listOf(Color(0xFF1FA84E), Color(0xFF1F7F8C))
    val people = state.members.size.takeIf { it > 0 } ?: (state.memberNames.size + 1)
    val subtitle = listOfNotNull(state.firstDay?.let { formatDayMonth(it) }, pluralStringResource(R.plurals.chat_header_people, people, people)).joinToString(" · ")
    ChatTopBar(
        title = state.name, onBack = onBack, subtitle = { ChatSubtitle(subtitle) },
        onTitleClick = state.meetupId?.let { id -> { onOpenMeetup(id) } },
        avatar = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(tint)),
                contentAlignment = Alignment.Center
            ) { Text(state.name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        },
        actions = {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (state.muted) MaterialTheme.colorScheme.background else terraFillColor().copy(alpha = 0.14f))
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.muted) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                    if (state.muted) stringResource(R.string.w_unmute) else stringResource(R.string.w_mute),
                    tint = if (state.muted) MaterialTheme.colorScheme.onSurfaceVariant else terraFillColor(), modifier = Modifier.size(22.dp)
                )
            }
        }
    )
}

/** Fila de pildoras bajo la cabecera: cómo llegar, invitar y ver quién va (abre la quedada). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupChatActions(state: GroupChatUiState, onDirections: () -> Unit, onInvite: (String) -> Unit, onOpenMeetup: (String) -> Unit) {
    val id = state.meetupId ?: return
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.schoolLat != null && state.schoolLon != null) {
            ChatActionPill(Icons.Outlined.Directions, stringResource(R.string.group_chat_screen_v3_como_llegar), onDirections)
        }
        ChatActionPill(Icons.Outlined.PersonAdd, stringResource(R.string.group_chat_screen_v4_invitar_al_grupo), { onInvite(id) })
        ChatActionPill(Icons.Outlined.Groups, pluralStringResource(R.plurals.chat_header_people, state.members.size, state.members.size), { onOpenMeetup(id) })
    }
}

@Composable
private fun ChatActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(terraFillColor().copy(alpha = 0.12f)).clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = terraFillColor(), modifier = Modifier.size(18.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = terraFillColor(), maxLines = 1)
    }
}

/**
 * Material del grupo en una tarjeta redondeada: cuadradito con la mochila, chips verdes con lo que ya hay y rojos con lo
 * que falta (o lo que lleva cada uno si no se pidio nada) y el boton «Mi material». Se toca para abrir «¿Qué llevas tú?».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupChatGearCard(lines: List<MeetupGearLine>, summary: String?, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).clip(shape).background(chatCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).clickable(onClick = onEdit).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(terraFillColor().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Luggage, null, tint = terraFillColor(), modifier = Modifier.size(20.dp))
        }
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lines.isNotEmpty()) lines.forEach { l ->
                val label = stringResource(gearLabelRes(l.item))
                GearChip(if (l.ok) "✓ $label" else stringResource(R.string.meetup_list_gear_missing, label.lowercase()), if (l.ok) Ok else MaterialTheme.colorScheme.error)
            } else if (summary != null) GearChip(summary, terraFillColor())
            else Text(stringResource(R.string.group_chat_screen_v2_sin_material_indicado), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.clip(CircleShape).background(terraFillColor()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.meetup_detail_my_gear), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun GearChip(text: String, color: Color) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
