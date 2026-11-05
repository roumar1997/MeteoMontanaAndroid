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

// Piezas de la ficha de quedada: fila de persona, avatar y diálogos de editar descripción y denunciar.

@Composable
internal fun MemberRow(member: MeetupMember, canKick: Boolean,
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
                Icon(Icons.Outlined.PersonRemove, stringResource(R.string.meetup_detail_kick), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showConfirm) {
        AlertDialog(onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.meetup_detail_screen_v2_expulsar_participante)) },
            text = { Text(stringResource(R.string.meetup_detail_screen_v2_expulsar_a_1_s, member.displayName ?: member.username ?: "")) },
            confirmButton = { TextButton(onClick = { showConfirm = false; onKick() }) {
                Text(stringResource(R.string.meetup_detail_screen_v2_expulsar), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.common_cancel).uppercase()) } })
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
internal fun EditDescriptionDialog(initial: String, saving: Boolean,
                                  onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meetup_detail_screen_v2_detalles_de_la_quedada)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.meetup_detail_screen_v2_material_nivel_punto_de_encuentro)) },
            minLines = 3, maxLines = 8) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = !saving) {
            if (saving) CircularProgressIndicator(Modifier.size(16.dp)) else Text(stringResource(R.string.common_save).uppercase()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel).uppercase()) } })
}

@Composable
internal fun ReportMeetupDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    val reasons = listOf("SPAM" to stringResource(R.string.report_dialog_v3_spam_o_publicidad), "INAPPROPRIATE" to "Contenido inapropiado",
        "HARASSMENT" to "Acoso", "OTHER" to stringResource(R.string.report_dialog_v3_otro_motivo))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.meetup_detail_screen_v2_denunciar_quedada)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.meetup_detail_screen_v2_selecciona_el_motivo), style = MaterialTheme.typography.bodyMedium)
            reasons.forEach { (code, label) -> TextButton(onClick = { onReport(code) },
                modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) } }
        } }, confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel).uppercase()) } })
}
