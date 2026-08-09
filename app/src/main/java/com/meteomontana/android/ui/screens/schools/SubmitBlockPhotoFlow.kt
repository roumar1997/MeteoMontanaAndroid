package com.meteomontana.android.ui.screens.schools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.content.ContextCompat
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
    var permisosPedidos by remember { mutableStateOf(false) }
    var galeriaLista by remember { mutableStateOf(false) }

    /**
     * Lo que hace falta para leer DONDE se hizo una foto:
     * - leer la galeria (si no, no hay fotos que ensenar);
     * - ACCESS_MEDIA_LOCATION (Android 10+), sin el cual el sistema entrega la
     *   imagen con las coordenadas borradas -- el sintoma es identico al de una
     *   foto que no las tiene, y por eso costo tanto dar con ello.
     */
    val necesarios = buildList {
        add(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    val permisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { concedidos ->
        // Si falta el de leer la galeria no se puede ensenar nada; el de la
        // ubicacion se avisa cuando falle la foto, no antes.
        galeriaLista = concedidos[necesarios.first()] == true
        if (!galeriaLista) {
            aviso = "Sin permiso para ver tus fotos no podemos saber dónde las hiciste."
        }
    }

    LaunchedEffect(Unit) {
        if (permisosPedidos) return@LaunchedEffect
        permisosPedidos = true
        val faltan = necesarios.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltan.isEmpty()) galeriaLista = true else permisos.launch(faltan.toTypedArray())
    }

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

    if (galeriaLista && aviso == null) {
        // A pantalla completa: emitida en medio del contenido de la lista se
        // quedaba sin sitio y no se veia nada.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false)
        ) {
            GaleriaReciente(onElegir = { elegida(it) }, onCancelar = onDismiss)
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
