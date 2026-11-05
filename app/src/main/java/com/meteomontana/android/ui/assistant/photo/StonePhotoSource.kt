package com.meteomontana.android.ui.assistant.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.meteomontana.android.ui.components.rememberSelectorDeFoto
import java.io.File

/** Las dos formas de dar una foto a «¿Qué piedra es esta?». */
class StonePhotoSource(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

/**
 * Cámara del sistema (TakePicture sobre un URI del FileProvider: no hace falta permiso de cámara) y selector de
 * documentos del sistema para la galería (conserva las coordenadas de la foto cuando el proveedor las deja).
 * [onPhoto] recibe la foto ya lista; si el usuario cancela, no se llama.
 */
@Composable
fun rememberStonePhotoSource(onError: () -> Unit = {}, onPhoto: (StonePhotoInput) -> Unit): StonePhotoSource {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending
        if (ok && uri != null) onPhoto(StonePhotoInput(uri, fromCamera = true))
    }
    val gallery = rememberSelectorDeFoto(onError = { onError() }) { uri ->
        if (uri != null) onPhoto(StonePhotoInput(uri, fromCamera = false))
    }
    return remember(camera, gallery) {
        StonePhotoSource(
            takePhoto = {
                val dir = File(context.cacheDir, "piedra-por-foto").apply { mkdirs() }
                val file = File(dir, "foto-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pending = uri
                runCatching { camera.launch(uri) }.onFailure { onError() }
            },
            pickPhoto = gallery
        )
    }
}
