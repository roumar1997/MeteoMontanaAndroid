package com.meteomontana.android.ui.screens.meetups

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonCard
import com.meteomontana.android.ui.components.SkeletonPersonRow
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.domain.model.MeetupMember
import com.meteomontana.android.ui.screens.chat.openDirections
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.SourceSerif4Family
import com.meteomontana.android.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetupDetailScreen(
    meetupId: String,
    onBack: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenSchool: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val genderGate by viewModel.genderGate.collectAsStateWithLifecycle()
    if (genderGate) {
        GenderGateDialog(onEditProfile = onEditProfile, onDismiss = { viewModel.clearGenderGate() })
    }
    val savingDescription by viewModel.savingDescription.collectAsStateWithLifecycle()
    val dayScores by viewModel.dayScores.collectAsStateWithLifecycle()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    var showReportDialog by remember { mutableStateOf(false) }
    var reportDone by remember { mutableStateOf(false) }
    var showEditDescription by remember { mutableStateOf(false) }
    var showEditGear by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(meetupId) { viewModel.loadMeetup(meetupId) }
    LaunchedEffect(state.meetup?.schoolId, state.meetup?.days) {
        val m = state.meetup ?: return@LaunchedEffect
        if (m.schoolId.isNotBlank() && m.days.isNotEmpty())
            viewModel.loadMeetupDayScores(m.schoolId, m.days.toSet())
    }

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.meetup_detail_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            com.meteomontana.android.ui.components.HelpButton(topicKey = "meetup_detail", compact = true)
            // Compartir
            state.meetup?.let { m ->
                IconButton(onClick = {
                    com.meteomontana.android.ui.share.shareMeetup(
                        context, m.name, m.schoolName, m.days, m.discipline,
                        m.memberCount, m.memberLimit, state.inviteLink)
                }) {
                    Icon(Icons.Outlined.Share, stringResource(R.string.common_share), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.meetup?.conversationId?.let { convId ->
                if (state.meetup?.joined == true || state.meetup?.creatorUid == myUid) {
                    IconButton(onClick = { onOpenChat(convId) }) {
                        Icon(Icons.Outlined.Chat, "Chat", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (state.meetup != null && state.meetup?.creatorUid != myUid) {
                IconButton(onClick = { showReportDialog = true }) {
                    Icon(Icons.Outlined.Flag, stringResource(R.string.common_report),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Diálogos
        if (showEditGear) {
            state.meetup?.let { m ->
                EditGearDialog(
                    discipline = m.discipline,
                    currentGearJson = m.members.firstOrNull { it.uid == myUid }?.gearJson,
                    onDismiss = { showEditGear = false },
                    onSave = { json ->
                        viewModel.updateMyGear(meetupId, json)
                        showEditGear = false
                    }
                )
            }
        }
        if (showEditDescription) {
            EditDescriptionDialog(
                initial = state.meetup?.description ?: "", saving = savingDescription,
                onDismiss = { showEditDescription = false },
                onSave = { viewModel.updateDescription(meetupId, it.ifBlank { null }); showEditDescription = false }
            )
        }
        if (showReportDialog) {
            ReportMeetupDialog(
                onDismiss = { showReportDialog = false },
                onReport = { reason ->
                    viewModel.report(meetupId, state.meetup?.creatorUid, reason, null,
                        onSuccess = { reportDone = true })
                    showReportDialog = false
                }
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.meetup_detail_delete)) },
                text = { Text(stringResource(R.string.meetup_detail_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMeetup(meetupId) { onBack() }
                    }) { Text(stringResource(R.string.meetup_detail_screen_v2_eliminar), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text(stringResource(R.string.meetup_detail_screen_v2_salir_de_la_quedada)) },
                text = { Text(stringResource(R.string.meetup_detail_screen_seguro_que_quieres_salir)) },
                confirmButton = {
                    TextButton(onClick = { showLeaveConfirm = false; viewModel.leave(meetupId) }) {
                        Text(stringResource(R.string.meetup_detail_screen_v2_salir), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.common_cancel).uppercase()) } }
            )
        }
        if (reportDone) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); reportDone = false }
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(Spacing.sm), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.meetup_detail_report_done), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        when {
            state.isLoading && state.meetup == null -> {
                Column { SkeletonList(count = 1, dividers = false) { SkeletonCard() }; SkeletonList(count = 5) { SkeletonPersonRow(avatar = 36.dp) } }
            }
            state.meetup == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.meetup_detail_screen_v2_no_se_pudo_cargar_la))
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = { viewModel.loadMeetup(meetupId) }) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            }
            else -> {
                val meetup = state.meetup!!
                val isCreator = meetup.creatorUid == myUid

                Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f)) {
                    // Foto
                    if (meetup.photoUrl != null) {
                        item {
                            AsyncImage(model = meetup.photoUrl, contentDescription = meetup.name,
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentScale = ContentScale.Crop)
                        }
                    }

                    // ── Hero: nombre + organizador + botones de acción ──
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(meetup.name, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold, fontFamily = SourceSerif4Family)

                            // Organizador
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.clickable { onOpenProfile(meetup.creatorUid) }) {
                                MemberAvatar(meetup.creatorPhotoUrl, 28.dp)
                                val creatorName = meetup.members.firstOrNull { it.uid == meetup.creatorUid }
                                    ?.let { it.displayName ?: it.username }
                                    ?: meetup.creatorUsername ?: AppText.get(R.string.w_organizer)
                                Column {
                                    Text(stringResource(R.string.meetup_detail_organized_by), style = EyebrowTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(creatorName, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                }
                            }

                            // Botones de acción
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                if (meetup.schoolLat != null && meetup.schoolLon != null) {
                                    Button(
                                        onClick = { openDirections(context, meetup.schoolLat!!, meetup.schoolLon!!, meetup.schoolName) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Icon(Icons.Outlined.Directions, null, Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.common_directions), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                meetup.schoolName?.let { schoolName ->
                                    OutlinedButton(
                                        onClick = { onOpenSchool(meetup.schoolId) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Icon(Icons.Outlined.Terrain, null, Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.common_view_school), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // ── Info con iconos ──
                    item {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            // Escuela
                            meetup.schoolName?.let { name ->
                                InfoRow(icon = { Icon(Icons.Outlined.Terrain, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary) }) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Días con scores
                            InfoRow(icon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary) }) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    meetup.days.forEach { day ->
                                        val score = dayScores[day]
                                        Row(modifier = Modifier.clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(formatDayMonth(day), style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium)
                                            if (score != null) {
                                                Box(Modifier.clip(RoundedCornerShape(2.dp))
                                                    .background(detailScoreColor(score))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                                    Text("$score", style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Privacidad + disciplina
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                if (meetup.privacy != "OPEN") {
                                    InfoRow(icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                                        Text(privacyLabel(meetup.privacy), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                meetup.discipline?.let {
                                    Text("· ${disciplineLabel(it)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // ── Descripción ──
                    item {
                        HorizontalDivider()
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(stringResource(R.string.meetup_detail_details), style = EyebrowTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isCreator) {
                                    IconButton(onClick = { showEditDescription = true }, Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (!meetup.description.isNullOrBlank()) {
                                Text(meetup.description!!, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(
                                    if (isCreator) stringResource(R.string.meetup_detail_screen_v3_anade_detalles_material_nivel_hora)
                                    else stringResource(R.string.meetup_detail_screen_v3_sin_detalles),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── Material (gear) ──
                    item {
                        HorizontalDivider()
                        GearSection(
                            meetup = meetup,
                            myUid = myUid,
                            isCreator = isCreator,
                            onEditGear = { showEditGear = true }
                        )
                    }

                    // ── Organizador / salir (unirse va FIJO abajo, sin scroll) ──
                    if (isCreator || meetup.joined) item {
                        HorizontalDivider()
                        Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                            if (isCreator) Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(stringResource(R.string.meetup_detail_screen_v2_eres_el_organizador),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(2.dp)
                                ) { Text(stringResource(R.string.meetup_detail_delete).uppercase()) }
                            } else OutlinedButton(onClick = { showLeaveConfirm = true },
                                enabled = !state.leaving, modifier = Modifier.fillMaxWidth()) {
                                if (state.leaving) CircularProgressIndicator(Modifier.size(16.dp))
                                else Text(stringResource(R.string.meetup_detail_leave))
                            }
                        }
                    }

                    // ── Participantes ──
                    item {
                        HorizontalDivider()
                        val limitText = meetup.memberLimit?.let { "${meetup.memberCount}/$it" }
                            ?: "${meetup.memberCount}"
                        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$limitText ${stringResource(R.string.meetup_detail_participants)}", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(meetup.members, key = { it.uid }) { member ->
                        MemberRow(member, canKick = isCreator && member.uid != myUid,
                            onProfileClick = { onOpenProfile(member.uid) },
                            onKick = { viewModel.kick(meetupId, member.uid) })
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                // ── UNIRME fijo (siempre visible, sin scroll) ──
                if (!isCreator && !meetup.joined) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Box(Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        when {
                            meetup.isFull -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.sm),
                                contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.meetup_detail_screen_v2_aforo_completo), style = EyebrowTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            meetup.privacy == "WOMEN" && viewModel.myGender.collectAsStateWithLifecycle().value != "WOMAN" -> {
                                Text(stringResource(R.string.meetup_detail_screen_solo_pueden_unirse_personas),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> Button(onClick = { viewModel.join(meetupId) }, enabled = !state.joining,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(2.dp)) {
                                if (state.joining) CircularProgressIndicator(Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary)
                                else Text(stringResource(R.string.meetup_detail_join))
                            }
                        }
                    }
                }
                }
            }
        }

        state.error?.let { err ->
            LaunchedEffect(err) { kotlinx.coroutines.delay(3000); viewModel.clearError() }
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)
                .padding(Spacing.sm), contentAlignment = Alignment.Center) {
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
internal fun InfoRow(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        icon()
        content()
    }
}

internal fun detailScoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E); score >= 60 -> Color(0xFFF59E0B)
    score >= 40 -> Color(0xFFEF4444); else -> Color(0xFF6B7280)
}

// ── Gear helpers ────────────────────────────────────────────────────────────

