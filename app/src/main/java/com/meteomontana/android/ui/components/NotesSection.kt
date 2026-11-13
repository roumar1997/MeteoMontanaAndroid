package com.meteomontana.android.ui.components

import com.meteomontana.android.ui.theme.inkButtonColor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.model.Note

@Composable
fun NotesSection(
    notes: List<Note>,
    onPublish: (String, FileRef?) -> Unit,
    onVote: (Note, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    schoolName: String? = null
) {
    val avatarVm: MyAvatarViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val myAvatar by avatarVm.photoUrl.collectAsStateWithLifecycle()
    val myName by avatarVm.name.collectAsStateWithLifecycle()
    // Nota cuya foto se está viendo a pantalla completa (null = ninguna).
    var photoNote by remember { mutableStateOf<Note?>(null) }
    // Orden por utilidad (▲ − ▼): más votadas primero por defecto; el orden vive en shared (NoteOrdering).
    var mostVotedFirst by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val ordered = remember(notes, mostVotedFirst) {
        com.meteomontana.android.domain.usecase.notes.NoteOrdering.byVotes(notes, mostVotedFirst)
    }
    // Moderación: denunciar notas ajenas (bandera) + ocultar al instante.
    val moderation: ModerationViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsStateWithLifecycle()
    var reportTarget by remember { mutableStateOf<Note?>(null) }
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.notes_section_v2_notas_comunitarias),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (notes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(if (notes.size == 1) stringResource(R.string.notes_count_one)
                         else stringResource(R.string.notes_count_many, notes.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.guideTarget("detail_notes.write")) {
            NoteComposer(schoolName, myAvatar, myName, myUid, onPublish)
        }
        if (notes.size > 1) {
            Spacer(Modifier.height(12.dp))
            // Orden por votos: más votadas ⇄ menos votadas.
            Text(
                (if (mostVotedFirst) stringResource(R.string.notes_order_most_voted) else stringResource(R.string.notes_order_least_voted)) + " ⇅",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = com.meteomontana.android.ui.theme.Mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 0.8.sp,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                    .clickable { mostVotedFirst = !mostVotedFirst }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .guideTarget("detail_notes.order")
            )
        }

        Column {
            Spacer(Modifier.height(8.dp))
            if (notes.isEmpty()) {
                Text(
                    stringResource(R.string.notes_section_v2_sin_notas_aun_se_el),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Ordenadas por utilidad (▲ − ▼) según el selector. Las denunciadas por ti se ocultan al instante.
                ordered.filter { "NOTE:${it.id}" !in hiddenIds }.forEach { n ->
                    NoteCard(n, onPhotoClick = { photoNote = n }, onVote = { v -> onVote(n, v) },
                        canReport = myUid != null && myUid != n.uid,
                        onReport = { reportTarget = n })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    photoNote?.let { n ->
        NotePhotoDialog(note = n, onDismiss = { photoNote = null })
    }

    reportTarget?.let { n ->
        ReportDialog(
            title = stringResource(R.string.notes_section_v2_denunciar_nota),
            authorLabel = n.author ?: stringResource(R.string.notes_section_v3_este_usuario),
            onReport = { reason, alsoBlock, details ->
                moderation.report("NOTE", n.id, reason, details = details,
                    alsoBlockUid = if (alsoBlock) n.uid else null)
            },
            onDismiss = { reportTarget = null }
        )
    }
}

/** Foto a pantalla completa con el texto de la nota debajo. */
@Composable
private fun NotePhotoDialog(note: Note, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = rememberListImageRequest(note.photoUrl),
                contentDescription = stringResource(R.string.notes_section_v2_foto_de_la_nota),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            Column(Modifier.padding(16.dp)) {
                Text(note.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(note.author ?: stringResource(R.string.notes_section_anonimo),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                Text("✕ ${stringResource(R.string.common_close).uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onDismiss))
            }
        }
    }
}

/** Mi foto y mi nombre para la caja de escribir (con cache offline del perfil publico). */
@dagger.hilt.android.lifecycle.HiltViewModel
class MyAvatarViewModel @javax.inject.Inject constructor(
    getPublicProfile: com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
) : androidx.lifecycle.ViewModel() {
    private val _photo = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val photoUrl: kotlinx.coroutines.flow.StateFlow<String?> = _photo
    private val _name = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val name: kotlinx.coroutines.flow.StateFlow<String?> = _name

    init {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) viewModelScope.launch {
            runCatching { getPublicProfile(uid) }.onSuccess { _photo.value = it.photoUrl; _name.value = it.displayName ?: it.username }
        }
    }
}
