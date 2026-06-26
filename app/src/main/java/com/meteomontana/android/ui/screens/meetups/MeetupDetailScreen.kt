package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.MeetupMember
import com.meteomontana.android.ui.theme.Spacing

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
    val myUid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(meetupId) { viewModel.loadMeetup(meetupId) }

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text(
                state.meetup?.name ?: "Quedada",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // Botón de chat
            state.meetup?.conversationId?.let { convId ->
                if (state.meetup?.joined == true || state.meetup?.creatorUid == myUid) {
                    IconButton(onClick = { onOpenChat(convId) }) {
                        Icon(Icons.Outlined.Chat, contentDescription = "Chat del grupo")
                    }
                }
            }
        }
        HorizontalDivider()

        when {
            state.isLoading && state.meetup == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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
                    // Foto de cabecera
                    if (meetup.photoUrl != null) {
                        item {
                            AsyncImage(
                                model = meetup.photoUrl,
                                contentDescription = meetup.name,
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Info principal
                    item {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Escuela
                            meetup.schoolName?.let { schoolName ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clickable { onOpenSchool(meetup.schoolId) }
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text("VER ESCUELA: $schoolName",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Fechas
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    meetup.days.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Privacidad + Disciplina
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                if (meetup.privacy != "OPEN") {
                                    InfoChip(
                                        icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(12.dp)) },
                                        label = privacyLabel(meetup.privacy)
                                    )
                                }
                                meetup.discipline?.let {
                                    InfoChip(label = disciplineLabel(it))
                                }
                            }

                            // Creador
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.clickable { onOpenProfile(meetup.creatorUid) }
                            ) {
                                MemberAvatar(meetup.creatorPhotoUrl, 24.dp)
                                Text(
                                    "Organiza: ${meetup.creatorUsername ?: meetup.creatorUid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDividerItem()

                    // Botón unirse / salir
                    item {
                        Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                            when {
                                isCreator -> {
                                    Text("Eres el organizador",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                meetup.joined -> {
                                    OutlinedButton(
                                        onClick = { viewModel.leave(meetupId) },
                                        enabled = !state.leaving,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (state.leaving) CircularProgressIndicator(Modifier.size(16.dp))
                                        else Text("SALIR DE LA QUEDADA")
                                    }
                                }
                                meetup.isFull -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(Spacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("AFORO COMPLETO",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = { viewModel.join(meetupId) },
                                        enabled = !state.joining,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (state.joining) CircularProgressIndicator(
                                            Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        else Text("UNIRSE A LA QUEDADA")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDividerItem()

                    // Cabecera de miembros
                    item {
                        val limitText = meetup.memberLimit?.let { "${meetup.memberCount}/$it" }
                            ?: "${meetup.memberCount}"
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$limitText participantes",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold)
                        }
                    }

                    // Lista de miembros
                    items(meetup.members, key = { it.uid }) { member ->
                        MemberRow(
                            member = member,
                            canKick = isCreator && member.uid != myUid,
                            onProfileClick = { onOpenProfile(member.uid) },
                            onKick = { viewModel.kick(meetupId, member.uid) }
                        )
                        HorizontalDivider()
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Error snack-like
        state.error?.let { err ->
            LaunchedEffect(err) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)
                    .padding(Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MeetupMember,
    canKick: Boolean,
    onProfileClick: () -> Unit,
    onKick: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MemberAvatar(member.photoUrl, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                member.displayName ?: member.username ?: member.uid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            member.username?.let {
                Text("@$it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canKick) {
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Outlined.PersonRemove, contentDescription = "Expulsar",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Expulsar participante") },
            text = { Text("¿Expulsar a ${member.displayName ?: member.username}?") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onKick() }) {
                    Text("EXPULSAR", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("CANCELAR") }
            }
        )
    }
}

@Composable
private fun MemberAvatar(photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        icon?.invoke()
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.HorizontalDividerItem() {
    item { HorizontalDivider() }
}
