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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.model.Note

@Composable
fun NotesSection(
    notes: List<Note>,
    onPublish: (String, FileRef?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Nota cuya foto se está viendo a pantalla completa (null = ninguna).
    var photoNote by remember { mutableStateOf<Note?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "NOTAS COMUNITARIAS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (notes.isEmpty()) {
            Text(
                "Sin notas aún. ¡Sé el primero!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        } else {
            notes.forEach { n ->
                NoteRow(n, onPhotoClick = { photoNote = n })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
            Spacer(Modifier.height(12.dp))
        }

        ComposerRow(onPublish = onPublish)
    }

    photoNote?.let { n ->
        NotePhotoDialog(note = n, onDismiss = { photoNote = null })
    }
}

@Composable
private fun NoteRow(n: Note, onPhotoClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(n.text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground)
        n.photoUrl?.let { url ->
            Spacer(Modifier.height(6.dp))
            AsyncImage(
                model = url,
                contentDescription = "Foto de la nota — tocar para ampliar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(onClick = onPhotoClick),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                n.author ?: "Anónimo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "▲ ${n.upvotesCount}  ▼ ${n.downvotesCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                model = note.photoUrl,
                contentDescription = "Foto de la nota",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            Column(Modifier.padding(16.dp)) {
                Text(note.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(note.author ?: "Anónimo",
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

@Composable
private fun ComposerRow(onPublish: (String, FileRef?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) photoUri = uri }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Comparte algo útil: orientación, secado, acceso...") },
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )
        Spacer(Modifier.height(8.dp))

        photoUri?.let { uri ->
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = "Foto adjunta",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { photoUri = null },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { photoLauncher.launch("image/*") }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (photoUri == null) "📷 FOTO" else "📷 CAMBIAR",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (text.isBlank()) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1C1C1A),
                        RoundedCornerShape(2.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(enabled = text.isNotBlank()) {
                        onPublish(text.trim(), photoUri?.let { FileRef(it.toString()) })
                        text = ""
                        photoUri = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Publicar",
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
