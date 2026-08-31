package com.meteomontana.android.ui.screens.schools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.util.PhotoPlacement
import com.meteomontana.android.ui.components.readPhotoLocation
import com.meteomontana.android.ui.theme.Spacing
import kotlinx.coroutines.tasks.await

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
    onDismiss: () -> Unit,
    /**
     * Si se entra DESDE una escuela (su mapa -> PROPONER), ya sabemos a cual
     * pertenece: no hay que buscarla por cercania ni rechazar la foto por estar
     * lejos del catalogo. Solo hace falta que la foto sepa donde se hizo.
     *
     * Es un parametro y no un flujo aparte porque el resto —selector, lectura
     * de coordenadas, semilla— es identico, y dos copias acaban divergiendo.
     */
    escuelaFijada: com.meteomontana.android.domain.model.School? = null
) {
    val context = LocalContext.current
    // Sin EXIF y SIN escuela fijada: no podemos adivinar dónde fue, pero el
    // usuario sí lo sabe — que la elija él de una lista, en vez de bloquear
    // sin alternativa (Rodrigo, 2026-08-21: caso real, foto reenviada por
    // WhatsApp — WhatsApp borra el EXIF de TODAS las fotos que reenvía, así
    // que ni Cumbre ni el propio Android pueden hacer nada con esa copia).
    var eligiendoEscuela by remember { mutableStateOf(false) }
    var uriPendiente by remember { mutableStateOf<Uri?>(null) }
    // Elegir origen ANTES de lanzar nada: cámara en el momento o galería
    // (Rodrigo, 2026-08-21: "que te permita hacerla en ese mismo momento").
    var eligiendoOrigen by remember { mutableStateOf(true) }
    var pidiendoUbicacionCamara by remember { mutableStateOf(false) }
    var cameraUriPendiente by remember { mutableStateOf<Uri?>(null) }

    fun seedYAbrir(schoolId: String, uri: Uri, lat: Double, lon: Double, aspect: String?) {
        seedStore.put(PhotoProposalSeed.Seed(schoolId, uri.toString(), lat, lon, aspect))
        onOpenSchool(schoolId)
    }

    fun procesar(uri: Uri, donde: com.meteomontana.android.ui.components.PhotoLocation?) {
        // Desde el mapa de una escuela: la escuela ya esta decidida, así que
        // ni siquiera hace falta la ubicación de la foto — si no la trae, se
        // usa el centro de la escuela como semilla y el punto se ajusta a
        // mano en el mapa. Pero hay que DECIRLO: colocarlo en el centro sin
        // avisar confunde (Rodrigo, 2026-08-21: "que te diga que puedes
        // ponerlo a mano pero que esa foto no tiene ubicación").
        if (escuelaFijada != null) {
            if (donde == null) {
                android.widget.Toast.makeText(
                    context,
                    "Esta foto no trae ubicación: coloca tú el punto en el mapa.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            seedYAbrir(escuelaFijada.id, uri, donde?.lat ?: escuelaFijada.lat,
                donde?.lon ?: escuelaFijada.lon,
                donde?.let { PhotoPlacement.aspectFromCameraDirection(it.cameraDegrees) })
            return
        }
        if (donde == null) {
            uriPendiente = uri
            eligiendoEscuela = true
            return
        }
        when (val r = PhotoPlacement.schoolFor(donde.lat, donde.lon, schools)) {
            is PhotoPlacement.Result.Found -> {
                seedYAbrir(r.school.id, uri, donde.lat, donde.lon,
                    PhotoPlacement.aspectFromCameraDirection(donde.cameraDegrees))
            }
            is PhotoPlacement.Result.NoSchoolNearby -> {
                // La foto SÍ trae ubicación, pero no cae cerca de ninguna escuela
                // del catálogo — igual que sin EXIF, mejor dejar elegir a mano
                // que bloquear sin salida.
                uriPendiente = uri
                eligiendoEscuela = true
            }
        }
    }

    fun elegida(uri: Uri) = procesar(uri, readPhotoLocation(context, uri))

    // El selector del sistema es una pantalla suya, no algo que dibujemos: se
    // lanza una sola vez y si el usuario sale sin elegir, se cierra todo el
    // flujo (equivale al "✕" de la rejilla que habia antes).
    val elegirFoto = com.meteomontana.android.ui.components.rememberSelectorDeFoto { uri ->
        if (uri == null) onDismiss() else elegida(uri)
    }

    // Cámara en el momento: la foto se guarda en un fichero propio (vía
    // FileProvider) y la ubicación NO sale del EXIF —lo escribe el propio
    // fabricante de la cámara, si le da la gana— sino del GPS del móvil AHORA
    // MISMO, que es justo lo que se necesita al fotografiar en la roca.
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUriPendiente
        if (ok && uri != null) { pidiendoUbicacionCamara = true }
        else onDismiss()
    }
    fun launchCamera() {
        val dir = java.io.File(context.cacheDir, "propuesta-piedra").apply { mkdirs() }
        val file = java.io.File(dir, "foto-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        cameraUriPendiente = uri
        runCatching { cameraLauncher.launch(uri) }
    }
    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* se comprueba el resultado leyendo el permiso directamente abajo */ }

    if (pidiendoUbicacionCamara) {
        val uri = cameraUriPendiente
        LaunchedEffect(uri) {
            if (uri == null) { onDismiss(); return@LaunchedEffect }
            val fino = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val aprox = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fino && !aprox) {
                locationPermLauncher.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                // Sin bloquear en seco: si el usuario deniega, se sigue sin
                // ubicación (mismo camino que una foto sin EXIF).
                kotlinx.coroutines.delay(400)
            }
            val fused = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(context)
            // CON TOPE: sin él, en interiores o con mala señal getCurrentLocation
            // puede tardar mucho más de lo razonable y parece que "no pasa nada"
            // tras hacer la foto (Rodrigo, 2026-08-22). A los 8 s se sigue sin
            // ubicación, por el mismo camino que una foto sin EXIF.
            val donde: com.meteomontana.android.ui.components.PhotoLocation? = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(8000) {
                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!ok) return@withTimeoutOrNull null
                    @Suppress("MissingPermission")
                    val loc = fused.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
                    ).await()
                    loc?.let { com.meteomontana.android.ui.components.PhotoLocation(it.latitude, it.longitude, null) }
                }
            }.getOrNull()
            pidiendoUbicacionCamara = false
            procesar(uri, donde)
        }
    }

    if (eligiendoOrigen) {
        // AlertDialog NATIVO en vez del propio hecho a mano: el propio no
        // respondía al toque en algunos móviles (Rodrigo, 2026-08-22: "no
        // funciona para poder pulsarlo") — el nativo es simple pero SIEMPRE
        // pulsable, que es lo que de verdad importa.
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { eligiendoOrigen = false; onDismiss() },
            title = { Text("¿Cómo quieres la foto?") },
            text = {
                Column {
                    androidx.compose.material3.TextButton(
                        onClick = { eligiendoOrigen = false; launchCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("HACER FOTO AHORA", modifier = Modifier.fillMaxWidth()) }
                    androidx.compose.material3.TextButton(
                        onClick = { eligiendoOrigen = false; elegirFoto() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ELEGIR DE GALERÍA", modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { eligiendoOrigen = false; onDismiss() }) { Text("CANCELAR") }
            }
        )
    }

    if (eligiendoEscuela) {
        var query by remember { mutableStateOf("") }
        val filtradas = remember(query, schools) {
            if (query.isBlank()) schools
            else schools.filter { it.name.contains(query, ignoreCase = true) }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { eligiendoEscuela = false; onDismiss() }) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.md)
            ) {
                Text("¿En qué escuela es esta piedra?",
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    "Esta foto no trae ubicación (frecuente si llegó por WhatsApp — " +
                        "borra esos datos al reenviarla). Elige la escuela y coloca " +
                        "el punto a mano en el mapa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar escuela…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .heightIn(max = 320.dp)
                ) {
                    items(filtradas, key = { it.id }) { escuela ->
                        Text(
                            escuela.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val uri = uriPendiente
                                    eligiendoEscuela = false
                                    if (uri != null) {
                                        seedYAbrir(escuela.id, uri, escuela.lat, escuela.lon, null)
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                TextButton(
                    onClick = { eligiendoEscuela = false; onDismiss() },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                ) { Text("CANCELAR") }
            }
        }
    }
}
