package com.meteomontana.android.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Elegir una foto del móvil, con el selector del SISTEMA.
 *
 * **Por qué el del sistema y no uno nuestro.** Hubo una rejilla propia que
 * preguntaba directamente a la galería, porque era la única forma de conservar
 * las coordenadas que la cámara guarda dentro de la foto. Google Play la
 * rechazó: su política de permisos de fotos y vídeos solo permite el acceso
 * amplio a la galería cuando los selectores del sistema no bastan para la
 * funcionalidad principal de la app, y proponer una piedra se puede hacer
 * igualmente colocando el punto a mano. La versión vc91 quedó bloqueada por
 * esto.
 *
 * De los selectores del sistema se usa el de DOCUMENTOS y no el de fotos:
 * ambos cumplen la política, pero el de fotos entrega siempre una copia con la
 * ubicación borrada, así que con él la escuela no se podría deducir nunca.
 * Cuánta información conserva el de documentos depende del proveedor que
 * responda —y de la capa del fabricante—, de modo que el resto de la app
 * nunca da por hecho que la foto traiga coordenadas.
 */
@Composable
fun rememberSelectorDeFoto(onResultado: (Uri?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onResultado(uri) }
    return remember(launcher) { { launcher.launch(TIPOS_DE_IMAGEN) } }
}

/**
 * Cualquier imagen. Sin restringir a JPEG a propósito: el usuario puede tener la
 * foto en HEIC (iPhone) o en WebP y el backend acepta lo que se le suba.
 */
private val TIPOS_DE_IMAGEN = arrayOf("image/*")
