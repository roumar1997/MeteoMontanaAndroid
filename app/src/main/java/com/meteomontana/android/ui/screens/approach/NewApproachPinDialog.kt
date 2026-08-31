package com.meteomontana.android.ui.screens.approach

import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.data.api.KtorPhotoApi
import com.meteomontana.android.data.api.dto.AddApproachPinRequest
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.ui.components.rememberSelectorDeFoto
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alta de una chincheta (foto y/o texto, NUNCA vacía — APPROACH_DESIGN.md
 * §2.3). Compartida entre "seguir" (admin añade sobre una aproximación ya
 * publicada) y "grabar" (donde queda en memoria hasta guardar el camino).
 * Espejo de NewApproachPinSheet/NewPinDraftSheet (iOS).
 */
private val KINDS = listOf(
    "FORK" to "◆ Bifurcación",
    "LANDMARK" to "● Referencia",
    "HAZARD" to "▲ Peligro",
    "KEY" to "★ Paso clave"
)

@HiltViewModel
class ApproachPinPhotoViewModel @Inject constructor(
    private val photoApi: KtorPhotoApi,
    private val fileReader: FileReader
) : ViewModel() {
    /** Sube la foto de una chincheta y devuelve su URL, o null si falla. */
    suspend fun upload(uri: Uri): String? = runCatching {
        val bytes = fileReader.readImageCompressed(FileRef(uri.toString()))
        photoApi.upload("approach-pin", bytes)
    }.getOrNull()
}

@Composable
fun NewApproachPinDialog(
    onDismiss: () -> Unit,
    /** El request ya trae kind/message/photoPath resueltos; lat/lon los pone el caller. */
    onSave: (AddApproachPinRequest) -> Unit,
    photoVm: ApproachPinPhotoViewModel = hiltViewModel()
) {
    var kind by remember { mutableStateOf("LANDMARK") }
    var message by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val pickGallery = rememberSelectorDeFoto { uri -> if (uri != null) photoUri = uri }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) pendingCameraUri?.let { photoUri = it } }
    fun launchCamera() {
        val dir = java.io.File(ctx.cacheDir, "approach-pins").apply { mkdirs() }
        val file = java.io.File(dir, "pin-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }

    val canSave = message.trim().isNotEmpty() || photoUri != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva chincheta", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text("TIPO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(KINDS) { (k, label) ->
                        val selected = kind == k
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (selected) Terra else MaterialTheme.colorScheme.surface)
                                .border(1.dp, Terra, RoundedCornerShape(2.dp))
                                .clickable { kind = k }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (selected)
                                androidx.compose.ui.graphics.Color.White else Terra)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                photoUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { launchCamera() }.padding(vertical = 10.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { Text("HACER FOTO", style = EyebrowTextStyle) }
                    Box(
                        modifier = Modifier.weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { pickGallery() }.padding(vertical = 10.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { Text("GALERÍA", style = EyebrowTextStyle) }
                }
                Spacer(Modifier.height(10.dp))
                Text("NOTA (opcional si hay foto)", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = message, onValueChange = { message = it },
                    placeholder = { Text("p. ej. En la bifurcación, a la derecha") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!canSave) {
                    Spacer(Modifier.height(6.dp))
                    Text("Añade una foto o una nota.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = 8.dp))
            } else {
                TextButton(enabled = canSave, onClick = {
                    scope.launch {
                        uploading = true
                        val photoPath = photoUri?.let { photoVm.upload(it) }
                        uploading = false
                        onSave(
                            AddApproachPinRequest(
                                lat = 0.0, lon = 0.0, positionIdx = 0,
                                kind = kind,
                                message = message.trim().ifBlank { null },
                                photoPath = photoPath
                            )
                        )
                    }
                }) { Text("GUARDAR") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}
