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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsState()
    val savingDescription by viewModel.savingDescription.collectAsState()
    val dayScores by viewModel.dayScores.collectAsState()
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
                Icon(Icons.Outlined.ArrowBack, "Volver")
            }
            Text(stringResource(R.string.meetup_detail_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            com.meteomontana.android.ui.components.HelpButton(topicKey = "meetup_detail")
            // Compartir
            state.meetup?.let { m ->
                IconButton(onClick = {
                    com.meteomontana.android.ui.share.shareMeetup(
                        context, m.name, m.schoolName, m.days, m.discipline,
                        m.memberCount, m.memberLimit)
                }) {
                    Icon(Icons.Outlined.Share, "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Outlined.Flag, "Denunciar",
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
                title = { Text("Eliminar quedada") },
                text = { Text("Se eliminará la quedada y su chat de grupo. ¿Continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMeetup(meetupId) { onBack() }
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text("Salir de la quedada") },
                text = { Text("¿Seguro que quieres salir? Puede que no puedas volver a unirte.") },
                confirmButton = {
                    TextButton(onClick = { showLeaveConfirm = false; viewModel.leave(meetupId) }) {
                        Text("SALIR", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("CANCELAR") } }
            )
        }
        if (reportDone) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); reportDone = false }
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(Spacing.sm), contentAlignment = Alignment.Center) {
                Text("Denuncia enviada", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        when {
            state.isLoading && state.meetup == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.meetup == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No se pudo cargar la quedada")
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = { viewModel.loadMeetup(meetupId) }) { Text("REINTENTAR") }
                    }
                }
            }
            else -> {
                val meetup = state.meetup!!
                val isCreator = meetup.creatorUid == myUid

                LazyColumn {
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
                                    ?: meetup.creatorUsername ?: "Organizador"
                                Column {
                                    Text("ORGANIZA", style = EyebrowTextStyle,
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
                                        Text("Como llegar", style = MaterialTheme.typography.labelMedium)
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
                                        Text("Ver escuela", style = MaterialTheme.typography.labelMedium)
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
                                Text("DETALLES", style = EyebrowTextStyle,
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
                                    if (isCreator) "Añade detalles (material, nivel, hora...)"
                                    else "Sin detalles",
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

                    // ── Unirse / salir ──
                    item {
                        HorizontalDivider()
                        Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                            when {
                                isCreator -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Text("Eres el organizador",
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
                                    ) { Text("ELIMINAR QUEDADA") }
                                }
                                meetup.joined -> OutlinedButton(onClick = { showLeaveConfirm = true },
                                    enabled = !state.leaving, modifier = Modifier.fillMaxWidth()) {
                                    if (state.leaving) CircularProgressIndicator(Modifier.size(16.dp))
                                    else Text("SALIR DE LA QUEDADA")
                                }
                                meetup.isFull -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.sm),
                                    contentAlignment = Alignment.Center) {
                                    Text("AFORO COMPLETO", style = EyebrowTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                meetup.privacy == "WOMEN" && viewModel.myGender.collectAsState().value != "WOMAN" -> {
                                    Text("Solo pueden unirse personas con género Mujer en su perfil.\nVe a Perfil → Editar perfil → Género.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> Button(onClick = { viewModel.join(meetupId) }, enabled = !state.joining,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(2.dp)) {
                                    if (state.joining) CircularProgressIndicator(Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary)
                                    else Text("UNIRSE A LA QUEDADA")
                                }
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
                            Text("$limitText PARTICIPANTES", style = EyebrowTextStyle,
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
private fun InfoRow(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        icon()
        content()
    }
}

@Composable
private fun MemberRow(member: MeetupMember, canKick: Boolean,
                      onProfileClick: () -> Unit, onKick: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onProfileClick)
        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MemberAvatar(member.photoUrl, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(member.displayName ?: member.username ?: member.uid,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            member.username?.let {
                Text("@$it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canKick) {
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Outlined.PersonRemove, "Expulsar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showConfirm) {
        AlertDialog(onDismissRequest = { showConfirm = false },
            title = { Text("Expulsar participante") },
            text = { Text("¿Expulsar a ${member.displayName ?: member.username}?") },
            confirmButton = { TextButton(onClick = { showConfirm = false; onKick() }) {
                Text("EXPULSAR", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("CANCELAR") } })
    }
}

@Composable
internal fun MemberAvatar(photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
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

@Composable
private fun EditDescriptionDialog(initial: String, saving: Boolean,
                                  onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Detalles de la quedada") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Material, nivel, punto de encuentro, hora…") },
            minLines = 3, maxLines = 8) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = !saving) {
            if (saving) CircularProgressIndicator(Modifier.size(16.dp)) else Text("GUARDAR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}

@Composable
private fun ReportMeetupDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    val reasons = listOf("SPAM" to "Spam o publicidad", "INAPPROPRIATE" to "Contenido inapropiado",
        "HARASSMENT" to "Acoso", "OTHER" to "Otro motivo")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Denunciar quedada") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Selecciona el motivo:", style = MaterialTheme.typography.bodyMedium)
            reasons.forEach { (code, label) -> TextButton(onClick = { onReport(code) },
                modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) } }
        } }, confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}

private fun detailScoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E); score >= 60 -> Color(0xFFF59E0B)
    score >= 40 -> Color(0xFFEF4444); else -> Color(0xFF6B7280)
}

// ── Gear helpers ────────────────────────────────────────────────────────────

internal fun parseGear(json: String?): Map<String, Int> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        json.trim('{', '}').split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim().toIntOrNull() ?: 0
                if (value > 0) key to value else null
            } else null
        }.toMap()
    } catch (_: Exception) { emptyMap() }
}

internal fun buildGearJson(gear: Map<String, Int>): String {
    val entries = gear.filter { it.value > 0 }
    if (entries.isEmpty()) return "{}"
    return "{" + entries.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}"
}

internal fun gearItemsForDiscipline(discipline: String?): List<Pair<String, String>> = when (discipline) {
    "BOULDER" -> listOf("crashpads" to "Crashpads")
    "ROUTE" -> listOf("cintas" to "Cintas", "cuerda" to "Cuerdas", "grigri" to "Gri-gri")
    else -> listOf("crashpads" to "Crashpads", "cintas" to "Cintas", "cuerda" to "Cuerdas", "grigri" to "Gri-gri")
}

internal fun gearLabel(key: String): String = when (key) {
    "crashpads" -> "crashpads"
    "cintas" -> "cintas"
    "cuerda" -> "cuerdas"
    "grigri" -> "gri-gri"
    else -> key
}

/** Suma el material de todos los miembros. */
internal fun totalGear(members: List<MeetupMember>): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    members.forEach { m ->
        parseGear(m.gearJson).forEach { (k, v) ->
            totals[k] = (totals[k] ?: 0) + v
        }
    }
    return totals.filter { it.value > 0 }
}

/** Texto compacto del material de un miembro (e.g. "2 crashpads, 12 cintas"). */
internal fun gearSummaryText(gearJson: String?): String {
    val gear = parseGear(gearJson)
    if (gear.isEmpty()) return ""
    return gear.entries.joinToString(" · ") { "${it.value} ${gearLabel(it.key)}" }
}

/** Texto compacto del total (para el banner del chat). */
internal fun totalGearSummary(members: List<MeetupMember>): String {
    val totals = totalGear(members)
    if (totals.isEmpty()) return ""
    return totals.entries.joinToString(" · ") { "${it.value} ${gearLabel(it.key)}" }
}

// ── Gear section composable ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearSection(
    meetup: com.meteomontana.android.domain.model.Meetup,
    myUid: String?,
    isCreator: Boolean,
    onEditGear: () -> Unit
) {
    val totals = totalGear(meetup.members)

    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Eyebrow
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Outlined.Luggage, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("MATERIAL", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Total pills
        if (totals.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                totals.forEach { (key, count) ->
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("$count", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(gearLabel(key), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("Nadie ha indicado material todavía. Pulsa abajo para añadir el tuyo.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Per-member breakdown
        meetup.members.forEach { member ->
            val gear = gearSummaryText(member.gearJson)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MemberAvatar(member.photoUrl, 28.dp)
                Text(member.displayName ?: member.username ?: member.uid.take(6),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false))
                if (gear.isNotEmpty()) {
                    Text(gear, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("sin material", style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        // Edit button (only if joined or creator)
        if (meetup.joined || isCreator) {
            OutlinedButton(
                onClick = onEditGear,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Editar mi material", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Edit gear dialog ────────────────────────────────────────────────────────

@Composable
internal fun EditGearDialog(
    discipline: String?,
    currentGearJson: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val items = gearItemsForDiscipline(discipline)
    val currentGear = parseGear(currentGearJson)
    val gearState = remember {
        mutableMapOf<String, Int>().apply {
            items.forEach { (key, _) -> put(key, currentGear[key] ?: 0) }
        }
    }
    // Force recomposition via a counter
    var version by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mi material") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Indica el material que llevas a la quedada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Trigger recomposition on version change
                @Suppress("UNUSED_VARIABLE") val ver = version
                items.forEach { (key, label) ->
                    GearStepper(
                        label = label,
                        value = gearState[key] ?: 0,
                        onMinus = {
                            val cur = gearState[key] ?: 0
                            if (cur > 0) { gearState[key] = cur - 1; version++ }
                        },
                        onPlus = {
                            val cur = gearState[key] ?: 0
                            gearState[key] = cur + 1; version++
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(buildGearJson(gearState)) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(2.dp)
            ) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
internal fun GearStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconButton(
                onClick = onMinus,
                modifier = Modifier.size(32.dp),
                enabled = value > 0
            ) {
                Icon(Icons.Outlined.Remove, "Menos", Modifier.size(18.dp))
            }
            Text("$value", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(
                onClick = onPlus,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Outlined.Add, "Mas", Modifier.size(18.dp))
            }
        }
    }
}
