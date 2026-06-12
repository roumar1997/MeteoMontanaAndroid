package com.meteomontana.android.ui.components

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Note

@Composable
fun NotesSection(
    notes: List<Note>,
    onPublish: (String, Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
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
                NoteRow(n)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
            Spacer(Modifier.height(12.dp))
        }

        ComposerRow(onPublish = onPublish)
    }
}

@Composable
private fun NoteRow(n: Note) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(n.text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground)
        if (n.photoUrl != null) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = n.photoUrl,
                contentDescription = "Foto adjunta a la nota",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
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

@Composable
private fun ComposerRow(onPublish: (String, Uri?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
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
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Foto seleccionada para la nota",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable {
                        if (photoUri == null) photoLauncher.launch("image/*") else photoUri = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (photoUri == null) "📷 Añadir foto" else "✕ Quitar foto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .weight(1f)
                    .background(
                        if (text.isBlank()) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1C1C1A),
                        RoundedCornerShape(2.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(enabled = text.isNotBlank()) {
                        onPublish(text.trim(), photoUri)
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
