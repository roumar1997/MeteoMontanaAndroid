package com.meteomontana.android.ui.screens.meetups

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupMember
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor

// Detalle de quedada moderno (maqueta 5): cabecera de color, chips, material del grupo como semáforo y participantes.

private val GymBlue = Color(0xFF3B6FD6)
private val GymPurple = Color(0xFF7A45C4)
private val CragGreen = Color(0xFF1FA84E)
private val CragTeal = Color(0xFF1F7F8C)

@Composable
private fun HeroRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = Color(0xFF2A2924), modifier = Modifier.size(20.dp)) }
}

@Composable
private fun HeroChip(text: String, fill: Color, textColor: Color = Color.White) {
    Text(
        text.uppercase(), style = EyebrowTextStyle, color = textColor, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(fill, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Cabecera de color con volver · chat · compartir (· denunciar), chips de estado, nombre y «día · lugar». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetupHero(
    meetup: Meetup, canChat: Boolean, canReport: Boolean,
    onBack: () -> Unit, onChat: () -> Unit, onShare: () -> Unit, onReport: () -> Unit
) {
    val colors = if (meetup.isGymMeetup) listOf(GymBlue, GymPurple) else listOf(CragGreen, CragTeal)
    val place = meetup.placeName ?: meetup.schoolName
    val subtitle = listOfNotNull(meetup.days.firstOrNull()?.let { formatDayMonth(it) }, place).joinToString(" · ")
    val spots = meetup.memberLimit?.let { (it - meetup.memberCount).coerceAtLeast(0) }
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(colors))) {
        meetup.photoUrl?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md, bottom = Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroRoundButton(Icons.Outlined.ArrowBack, stringResource(R.string.common_back), onBack)
                Box(Modifier.weight(1f))
                if (canChat) HeroRoundButton(Icons.Outlined.Chat, stringResource(R.string.meetup_open_chat), onChat)
                HeroRoundButton(Icons.Outlined.Share, stringResource(R.string.common_share), onShare)
                if (canReport) HeroRoundButton(Icons.Outlined.Flag, stringResource(R.string.common_report), onReport)
            }
            FlowRow(Modifier.padding(top = Spacing.xl), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                meetup.discipline?.let { HeroChip(disciplineLabel(it), Color.White.copy(alpha = 0.22f)) }
                HeroChip(privacyLabel(meetup.privacy), Color.White.copy(alpha = 0.22f))
                when {
                    meetup.isFull -> HeroChip(stringResource(R.string.meetup_full_chip), Color(0xFFB45309))
                    spots != null -> HeroChip(pluralStringResource(R.plurals.meetup_spots_left, spots, spots), CragGreen)
                }
            }
            Text(
                meetup.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White,
                modifier = Modifier.padding(top = 10.dp), lineHeight = 32.sp
            )
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}


@Composable
private fun InfoChip(text: String, dotsFor: List<String> = emptyList()) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dotsFor.forEach { g -> Box(Modifier.size(11.dp).clip(CircleShape).background(gradeChipStyle(g).stroke).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Nivel (con los colores de grado de la app), cuántos van y los compañeros buscados. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetupChips(meetup: Meetup) {
    val min = meetup.gradeMin
    val max = meetup.gradeMax ?: min
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (min != null) {
            val label = if (min.equals(max, true)) min.uppercase() else "${min.uppercase()}–${max?.uppercase()}"
            InfoChip(label, if (min.equals(max, true)) listOf(min) else listOfNotNull(min, max))
        }
        InfoChip(
            meetup.memberLimit?.let { stringResource(R.string.meetup_chip_going_of, meetup.memberCount, it) }
                ?: pluralStringResource(R.plurals.meetup_chip_going, meetup.memberCount, meetup.memberCount)
        )
        if (meetup.isRopeTeam) InfoChip(stringResource(R.string.meetup_facts_rope_team))
        else meetup.partnersWanted?.let { InfoChip(pluralStringResource(R.plurals.meetup_facts_partners, it, it)) }
    }
}

@Composable
internal fun ModernCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md).clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

/** «Material del grupo»: un recuadro por cosa pedida, en verde si está cubierta y en terracota si falta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GearSemaphore(meetup: Meetup, canEdit: Boolean, onEdit: () -> Unit) {
    val covered = meetup.gearCoverage.count { it.ok }
    ModernCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meetup_gear_group).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.meetup_gear_covered, covered, meetup.gearCoverage.size), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
            meetup.gearCoverage.forEach { c ->
                val ok = c.ok
                val bg = if (ok) Color(0xFF1FA84E).copy(alpha = 0.16f) else terraFillColor().copy(alpha = 0.16f)
                val fg = if (ok) Color(0xFF16803C) else terraFillColor()
                Column(
                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.PriorityHigh, null, tint = fg, modifier = Modifier.size(22.dp))
                    Text(context.getString(gearLabelRes(c.item)), fontSize = 12.sp, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${c.provided}/${c.required}", fontSize = 11.sp, color = fg)
                }
            }
        }
    }
}

/** Participantes: avatar, nombre (con «Organiza»), lo que lleva cada uno y expulsar si eres el organizador. */
@Composable
internal fun ParticipantsCard(
    meetup: Meetup, myUid: String?, canKick: Boolean, canEditGear: Boolean,
    onOpenProfile: (String) -> Unit, onKick: (String) -> Unit, onEditGear: () -> Unit
) {
    var kicking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<MeetupMember?>(null) }
    ModernCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meetup_detail_participants), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (canEditGear) Text(
                stringResource(R.string.meetup_detail_my_gear) + " ›", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = terraFillColor(),
                modifier = Modifier.clickable(onClick = onEditGear).padding(vertical = 4.dp)
            )
        }
        meetup.members.forEach { m ->
            val gear = gearSummaryText(m.gearJson)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenProfile(m.uid) }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemberAvatar(m.photoUrl, 40.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.displayName ?: m.username ?: m.uid.take(6), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (m.uid == meetup.creatorUid) Text(
                            stringResource(R.string.meetup_detail_organized_by_short).uppercase(), style = EyebrowTextStyle, color = terraFillColor(),
                            modifier = Modifier.clip(CircleShape).background(terraFillColor().copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (gear.isNotEmpty()) Text(gear, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canKick && m.uid != myUid) Icon(
                    Icons.Outlined.PersonRemove, stringResource(R.string.meetup_detail_kick), tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp).clickable { kicking = m }.padding(7.dp)
                )
            }
        }
    }
    kicking?.let { m ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { kicking = null },
            title = { Text(stringResource(R.string.meetup_detail_screen_v2_expulsar_participante)) },
            text = { Text(stringResource(R.string.meetup_detail_screen_v2_expulsar_a_1_s, m.displayName ?: m.username ?: "")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { kicking = null; onKick(m.uid) }) {
                    Text(stringResource(R.string.meetup_detail_screen_v2_expulsar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { kicking = null }) { Text(stringResource(R.string.common_cancel).uppercase()) } }
        )
    }
}

/** Detalles escritos por el organizador, con lápiz para editarlos. */
@Composable
internal fun DescriptionCard(description: String?, isCreator: Boolean, onEdit: () -> Unit) {
    if (description.isNullOrBlank() && !isCreator) return
    ModernCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meetup_detail_details), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (isCreator) Icon(
                Icons.Outlined.Edit, stringResource(R.string.common_edit), tint = terraFillColor(),
                modifier = Modifier.size(32.dp).clickable(onClick = onEdit).padding(6.dp)
            )
        }
        Text(
            description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.meetup_detail_screen_v3_anade_detalles_material_nivel_hora),
            fontSize = 14.sp, color = if (description.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Días propuestos con la nota de cada uno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DaysRow(days: List<String>, scores: Map<String, Int>) {
    if (days.size < 2 && scores.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            val s = scores[d]
            Row(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(formatDayMonth(d), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (s != null) Text(
                    "$s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    modifier = Modifier.clip(CircleShape).background(detailScoreColor(s)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
