package com.meteomontana.android.ui.screens.schools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.domain.util.PhotoPlacement
import com.meteomontana.android.ui.components.readPhotoLocation
import com.meteomontana.android.ui.theme.Spacing

/**
 * "Enviar piedra": eliges una foto y la app deduce en qué escuela se hizo.
 *
 * La cámara guarda las coordenadas dentro de la foto, así que no hace falta
 * buscar la escuela en la lista. A partir de ahí sigue el flujo de proponer
 * piedra de siempre —dibujar vías, orientación, más caras—, con la foto ya
 * puesta como primera cara.
 *
 * Lo que NO hace, y es a propósito: **elegir la piedra**. El GPS de un móvil se
 * equivoca entre 10 y 30 metros en un canchal, y las piedras están a metros unas
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
    var lanzado by remember { mutableStateOf(false) }
    // Estado en vez de un lambda con lateinit: el selector se abre cuando esto
    // se pone a true, venga del permiso o de que ya estuviera concedido.
    var abrirSelector by remember { mutableStateOf(false) }

    // En Android 10+ hay que PEDIR el permiso de ubicacion de las fotos: sin el,
    // el sistema entrega la imagen con las coordenadas borradas y parece que la
    // foto no las tuviera. Se pide justo antes de abrir el selector.
    val permiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> abrirSelector = true }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        val donde = readPhotoLocation(context, uri)
        if (donde == null) {
            aviso = context.getString(R.string.photo_no_coords)
            return@rememberLauncherForActivityResult
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

    // El selector del sistema no necesita permiso de fototeca: solo entrega la
    // foto elegida. Lo que hace falta es ACCESS_MEDIA_LOCATION, para que esa
    // foto conserve sus coordenadas.
    LaunchedEffect(Unit) {
        if (!lanzado) {
            lanzado = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                // Si lo deniega, se sigue igualmente: la foto puede traer las
                // coordenadas de todas formas, y si no, el aviso lo explica.
                permiso.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                abrirSelector = true
            }
        }
    }

    LaunchedEffect(abrirSelector) {
        if (abrirSelector) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
