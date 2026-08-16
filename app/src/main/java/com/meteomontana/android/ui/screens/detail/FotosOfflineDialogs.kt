package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Los tres avisos de las fotos para ver una escuela sin cobertura: la oferta,
 * el progreso y el resultado si algo falló.
 *
 * Separado de la pantalla porque es una conversación completa con el usuario y
 * `SchoolDetailScreen` ya es larga de sobra.
 */
@Composable
fun FotosOfflineDialogs(
    oferta: OfertaFotosOffline?,
    progreso: Float?,
    fallidas: Int?,
    onDescargar: () -> Unit,
    onRechazar: () -> Unit,
    onCerrarAviso: () -> Unit
) {
    // 1. ¿Bajamos las fotos?
    oferta?.let { o ->
        AlertDialog(
            onDismissRequest = onRechazar,
            title = { Text("¿Guardar también las fotos?") },
            text = {
                Text(
                    "La escuela ya está guardada. Bajar sus ${o.cuantas} fotos " +
                        "(${enMegas(o.bytesEstimados)}) te deja ver los topos en la roca " +
                        "aunque no haya cobertura.\n\n" +
                        "Si dices que no, tendrás los nombres, los grados y las líneas, " +
                        "pero no las fotos sobre las que van dibujadas."
                )
            },
            confirmButton = { TextButton(onClick = onDescargar) { Text("DESCARGAR") } },
            dismissButton = { TextButton(onClick = onRechazar) { Text("AHORA NO") } }
        )
    }

    // 2. Bajando. Sin botón de cerrar: termina en segundos y cancelar a medias
    //    dejaría la escuela con unas fotos sí y otras no, que es lo peor.
    progreso?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Guardando las fotos…") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Puedes seguir usando la app.")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { p.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${(p * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {}
        )
    }

    // 3. Faltaron algunas: se dice. Callar sería prometer unas fotos que no
    //    están, y eso se descubre ya sin cobertura, cuando no tiene arreglo.
    fallidas?.let { n ->
        AlertDialog(
            onDismissRequest = onCerrarAviso,
            title = { Text("Faltaron algunas fotos") },
            text = {
                Text(
                    if (n == 1) "Una foto no se pudo guardar. Vuelve a guardar la escuela con mejor cobertura y se reintentará solo esa."
                    else "$n fotos no se pudieron guardar. Vuelve a guardar la escuela con mejor cobertura y se reintentarán solo esas."
                )
            },
            confirmButton = { TextButton(onClick = onCerrarAviso) { Text("ENTENDIDO") } }
        )
    }
}

/** "7,0 MB" / "820 KB" — en la unidad que el usuario entiende de un vistazo. */
private fun enMegas(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) "${(bytes / 1024.0).toInt()} KB"
    else "${((mb * 10).toInt() / 10.0).toString().replace('.', ',')} MB"
}
