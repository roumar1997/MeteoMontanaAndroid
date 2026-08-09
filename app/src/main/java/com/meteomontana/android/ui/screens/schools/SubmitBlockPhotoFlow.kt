package com.meteomontana.android.ui.screens.schools

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.meteomontana.android.R
import com.meteomontana.android.domain.util.PhotoPlacement
import com.meteomontana.android.ui.components.readPhotoLocation
import com.meteomontana.android.ui.theme.Spacing

/**
 * "Enviar piedra": eliges una foto y la app deduce en que escuela se hizo.
 *
 * La camara guarda las coordenadas dentro de la foto, asi que no hace falta
 * buscar la escuela en la lista. A partir de ahi sigue el flujo de proponer
 * piedra de siempre, con la foto ya puesta como primera cara.
 *
 * Lo que NO hace, y es a proposito: **elegir la piedra**. El GPS de un movil se
 * equivoca entre 10 y 30 metros en un canchal y las piedras estan a metros unas
 * de otras. Sirve para acertar la escuela; el punto exacto lo pone el usuario
 * sobre el mapa, que se abre centrado donde se hizo la foto.
 */
@Composable
fun SubmitBlockPhotoFlow(
    schools: List<com.meteomontana.android.domain.model.School>,
    seedStore: PhotoProposalSeed,
    onOpenSchool: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var aviso by remember { mutableStateOf<String?>(null) }

    fun elegida(uri: Uri) {
        val donde = readPhotoLocation(context, uri)
        if (donde == null) {
            aviso = context.getString(R.string.photo_no_coords)
            return
        }
        when (val r = PhotoPlacement.schoolFor(donde.lat, donde.lon, schools)) {
            is PhotoPlacement.Result.Found -> {
                seedStore.put(
                    PhotoProposalSeed.Seed(
                        schoolId = r.school.id,
                        photoUri = uri.toString(),
                        lat = donde.lat,
                        lon = donde.lon,
                        aspect = PhotoPlacement.aspectFromCameraDirection(donde.cameraDegrees)
                    )
                )
                onOpenSchool(r.school.id)
            }
            is PhotoPlacement.Result.NoSchoolNearby -> {
                val lejos = r.nearestKm
                    ?.let { if (it >= 10) "a ${it.toInt()} km" else "a %.1f km".format(it) }
                    ?: "no hay ninguna en el catálogo"
                aviso = context.getString(R.string.photo_no_school, lejos)
            }
        }
    }

    // El selector del sistema es una pantalla suya, no algo que dibujemos: se
    // lanza una sola vez al entrar y si el usuario sale sin elegir, se cierra
    // todo el flujo (equivale al "✕" de la rejilla que habia antes).
    val elegirFoto = com.meteomontana.android.ui.components.rememberSelectorDeFoto { uri ->
        if (uri == null) onDismiss() else elegida(uri)
    }
    var lanzado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!lanzado) {
            lanzado = true
            elegirFoto()
        }
    }

    aviso?.let { texto ->
        AlertDialog(
            onDismissRequest = { aviso = null; onDismiss() },
            confirmButton = {
                TextButton(onClick = { aviso = null; onDismiss() }) { Text("ENTENDIDO") }
            },
            title = { Text("No se puede ubicar la foto") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xs)) {
                    Text(texto, style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}
