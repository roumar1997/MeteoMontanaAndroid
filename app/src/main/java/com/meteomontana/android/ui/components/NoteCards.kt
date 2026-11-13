package com.meteomontana.android.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.ui.screens.community.relativeTime

/** Foto redonda con la aureola de su titulo (o la inicial si no tiene foto): la misma que el feed. */
@Composable
internal fun PersonAvatar(photoUrl: String?, name: String?, uid: String?, size: androidx.compose.ui.unit.Dp) {
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.userRing(uid, size).size(size).clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(name?.trim()?.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Una nota con el aspecto de una publicacion del feed: autor, texto, foto y votos de utilidad. */
@Composable
internal fun NoteCard(n: Note, onPhotoClick: () -> Unit, onVote: (Int) -> Unit,
                      canReport: Boolean, onReport: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonAvatar(n.authorPhotoUrl, n.author, n.uid, 36.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(n.author ?: stringResource(R.string.notes_section_anonimo),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                    VerifiedCheck(n.uid, 14.dp)
                }
                val handle = n.authorUsername?.let { "@$it · " }.orEmpty()
                Text(handle + relativeTime(n.createdAt), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (canReport) {
                Icon(Icons.Outlined.Flag, stringResource(R.string.notes_section_v2_denunciar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onReport).padding(11.dp).size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        n.photoUrl?.let { url ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = url, contentDescription = stringResource(R.string.notes_section_v2_foto_de_la_nota_tocar),
                modifier = Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(12.dp)).clickable(onClick = onPhotoClick),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(10.dp))
        // Voto de utilidad: tocar de nuevo tu voto lo retira.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VoteChip("▲ ${n.upvotesCount}", active = n.myVote == 1) { onVote(1) }
            VoteChip("▼ ${n.downvotesCount}", active = n.myVote == -1) { onVote(-1) }
        }
    }
}

@Composable
private fun VoteChip(label: String, active: Boolean, onClick: () -> Unit) {
    val terra = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) terra.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (active) terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (active) terra else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Caja para escribir arriba: mi avatar, camara redonda y enviar redondo terracota. */
@Composable
internal fun NoteComposer(schoolName: String?, myPhotoUrl: String?, myName: String?, myUid: String?,
                          onPublish: (String, FileRef?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoUri = uri
    }
    val terra = MaterialTheme.colorScheme.primary
    val canSend = text.isNotBlank()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonAvatar(myPhotoUrl, myName, myUid, 32.dp)
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        if (schoolName != null) stringResource(R.string.notes_write_hint, schoolName)
                        else stringResource(R.string.notes_section_comparte_algo_util_orientacion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                    )
                }
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(terra), maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.notes_add_photo),
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (canSend) terra else terra.copy(alpha = 0.35f))
                    .clickable(enabled = canSend) {
                        onPublish(text.trim(), photoUri?.let { FileRef(it.toString()) })
                        text = ""; photoUri = null
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Send, stringResource(R.string.notes_send),
                tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        photoUri?.let { uri ->
            Spacer(Modifier.height(8.dp))
            Box {
                AsyncImage(
                    model = uri, contentDescription = stringResource(R.string.notes_section_v2_foto_adjunta),
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)).clickable { photoUri = null },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
