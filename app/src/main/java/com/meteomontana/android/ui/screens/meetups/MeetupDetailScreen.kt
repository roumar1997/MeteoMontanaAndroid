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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.domain.model.MeetupMember
import com.meteomontana.android.ui.screens.chat.openDirections
import com.meteomontana.android.ui.screens.gyms.GymActionPill
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
        // Diálogos
        if (showEditGear) {
            state.meetup?.let { m ->
                EditGearDialog(
                    discipline = m.discipline,
                    gym = m.isGymMeetup,
                    placeName = m.placeName ?: m.schoolName,
                    members = m.members,
                    myUid = myUid,
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
                val canChat = isCreator || meetup.joined
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            MeetupHero(
                                meetup, canChat = canChat && meetup.conversationId.isNotBlank(), canReport = !isCreator,
                                onBack = onBack, onChat = { onOpenChat(meetup.conversationId) }, onReport = { showReportDialog = true },
                                onShare = {
                                    com.meteomontana.android.ui.share.shareMeetup(
                                        context, meetup.name, meetup.placeName ?: meetup.schoolName, meetup.days, meetup.discipline,
                                        meetup.memberCount, meetup.memberLimit, state.inviteLink)
                                }
                            )
                        }
                        item { MeetupChips(meetup) }
                        item { DaysRow(meetup.days, dayScores) }
                        item { DescriptionCard(meetup.description, isCreator) { showEditDescription = true } }
                        if (meetup.gearCoverage.isNotEmpty()) item {
                            GearSemaphore(meetup, canEdit = canChat) { showEditGear = true }
                        }
                        item {
                            ParticipantsCard(
                                meetup, myUid, canKick = isCreator, canEditGear = canChat,
                                onOpenProfile = onOpenProfile, onKick = { viewModel.kick(meetupId, it) }, onEditGear = { showEditGear = true }
                            )
                        }
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (canChat && meetup.conversationId.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GymActionPill(stringResource(R.string.meetup_chat_n, meetup.memberCount), { onOpenChat(meetup.conversationId) }, Modifier.weight(1.4f), primary = true)
                                    if (!isCreator) GymActionPill(stringResource(R.string.meetup_detail_screen_v2_salir), { showLeaveConfirm = true }, Modifier.weight(1f), enabled = !state.leaving)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (meetup.schoolLat != null && meetup.schoolLon != null) GymActionPill(
                                        stringResource(R.string.common_directions), { openDirections(context, meetup.schoolLat!!, meetup.schoolLon!!, meetup.schoolName) },
                                        Modifier.weight(1f), icon = R.drawable.ic_directions
                                    )
                                    meetup.schoolName?.takeIf { !meetup.isGymMeetup }?.let {
                                        GymActionPill(stringResource(R.string.common_view_school), { onOpenSchool(meetup.schoolId) }, Modifier.weight(1f))
                                    }
                                }
                                if (isCreator) Text(
                                    stringResource(R.string.meetup_detail_delete), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().clickable { showDeleteConfirm = true }.padding(Spacing.sm)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                    // ── UNIRME fijo (siempre visible, sin scroll) ──
                    if (!isCreator && !meetup.joined) {
                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                            when {
                                meetup.isFull -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.sm), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.meetup_detail_screen_v2_aforo_completo), style = EyebrowTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                meetup.privacy == "WOMEN" && viewModel.myGender.collectAsStateWithLifecycle().value != "WOMAN" -> {
                                    Text(stringResource(R.string.meetup_detail_screen_solo_pueden_unirse_personas),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(com.meteomontana.android.ui.theme.terraFillColor())
                                        .clickable(enabled = !state.joining) { viewModel.join(meetupId) }.padding(vertical = 15.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.joining) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                                    else Text(stringResource(R.string.meetup_detail_join), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

