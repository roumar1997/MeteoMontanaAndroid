package com.meteomontana.android.ui.screens.schools

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rejilla con tus últimas fotos, preguntándole directamente a la galería.
 *
 * **Por qué una propia y no el selector del sistema.** Los selectores entregan
 * una COPIA de la foto y cada uno la trata distinto: el nuevo de Google borra la
 * ubicación siempre; "Fotos" devuelve un fichero temporal de su proveedor; la
 * galería del móvil, según la versión, una cosa u otra. Resultado: la app decía
 * "esta foto no guarda dónde se hizo" en fotos que sí la guardan — comprobado en
 * el móvil de Rodrigo, cuya foto llevaba coordenadas y con el permiso concedido.
 *
 * Preguntando nosotros, la foto es la REAL de la galería y sobre ella sí se
 * puede pedir el original con su EXIF intacto. Es más código, pero es el único
 * camino que se comporta igual en todos los móviles.
 */
@Composable
fun GaleriaReciente(
    onElegir: (Uri) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var fotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        fotos = withContext(Dispatchers.IO) { ultimasFotos(context) }
        cargando = false
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text("ELIGE UNA FOTO TUYA", style = EyebrowTextStyle, color = Terra)
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onCancelar)
            )
        }
        Text(
            "Tiene que ser una foto hecha por ti con la ubicación activada en la " +
                "cámara: de ahí sale en qué escuela estabas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )
        when {
            cargando -> Mensaje("Cargando tus fotos…")
            fotos.isEmpty() -> Mensaje("No hemos encontrado fotos en este móvil.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(Spacing.xs)
            ) {
                items(fotos) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Foto de la galería",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onElegir(uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Mensaje(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.md)
    )
}

/**
 * Todas las fotos de la galería, de más nueva a más vieja.
 *
 * Sin tope a propósito: la piedra puede salir de una foto de hace meses, y
 * cortar por las últimas 120 dejaría fuera justo esos casos. La rejilla es
 * perezosa, así que solo se cargan las miniaturas que se ven.
 */
private fun ultimasFotos(context: Context): List<Uri> = runCatching {
    val cols = arrayOf(MediaStore.Images.Media._ID)
    val orden = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, orden
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val out = ArrayList<Uri>(c.count)
        while (c.moveToNext()) {
            out.add(
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)
                )
            )
        }
        out
    } ?: emptyList()
}.getOrDefault(emptyList())
