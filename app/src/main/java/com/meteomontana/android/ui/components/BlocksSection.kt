package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel

@Composable
fun BlocksSection(
    blocks: List<Block>,
    onAddBlock: () -> Unit,
    onBlockClick: (String) -> Unit = {},
    schoolLat: Double? = null,
    schoolLon: Double? = null,
    schoolName: String = "",
    schoolId: String = "",
    viewModel: SchoolDetailViewModel? = null,
    onMyProposals: () -> Unit = {}
) {
    if (schoolLat == null || schoolLon == null || viewModel == null) return
    // Foto de "Enviar piedra": se lee UNA vez al componer, y el mapa se encarga
    // de abrir el flujo de proponer con ella.
    // ESTADO, no valor fijo: ademas de la foto que llega desde la lista de
    // escuelas, ahora se puede elegir una AQUI mismo (PROPONER -> "piedra desde
    // una foto"), y el mapa reacciona al cambio para abrir el flujo.
    // `remember` a secas, NO rememberSaveable: PhotoSeed no es de los tipos que
    // Compose sabe guardar al rotar, y al intentarlo reventaba justo al cambiar
    // la pantalla — el mapa dejaba de poder cerrarse (lo cazo Rodrigo).
    // Si el sistema mata el proceso a mitad se pierde la foto elegida, que es
    // exactamente lo que pasaba antes: no se empeora nada.
    var photoSeed by androidx.compose.runtime.remember(schoolId) {
        androidx.compose.runtime.mutableStateOf(
            viewModel.takePhotoSeed()?.let {
                com.meteomontana.android.ui.screens.detail.PhotoSeed(
                    photoUri = android.net.Uri.parse(it.photoUri),
                    lat = it.lat, lon = it.lon, aspect = it.aspect)
            }
        )
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val borradores = androidx.compose.runtime.remember(ctx) {
        com.meteomontana.android.ui.screens.detail.BoulderDraftStore(ctx)
    }
    var borradorPiedra by androidx.compose.runtime.remember(schoolId) {
        androidx.compose.runtime.mutableStateOf(borradores.load(schoolId))
    }
    // Aqui la escuela YA se conoce: solo hace falta que la foto sepa donde se
    // hizo. Nada de buscarla por cercania como en la lista de escuelas.
    val elegirFotoDePiedra = com.meteomontana.android.ui.components.rememberSelectorDeFoto { uri ->
        if (uri != null) {
            val donde = com.meteomontana.android.ui.components.readPhotoLocation(ctx, uri)
            if (donde == null) {
                android.widget.Toast.makeText(
                    ctx, ctx.getString(com.meteomontana.android.R.string.photo_no_coords),
                    android.widget.Toast.LENGTH_LONG).show()
            } else if (com.meteomontana.android.domain.util.Geo.haversineKm(
                    donde.lat, donde.lon, schoolLat, schoolLon
                ) > com.meteomontana.android.domain.util.PhotoPlacement.RADIO_ESCUELA_KM) {
                // La piedra se coloca DONDE SE HIZO LA FOTO. Si la foto es de
                // otro sitio, acabaria en el mapa de esta escuela a kilometros
                // de ella. Paso este control al entrar desde la escuela pensando
                // que "ya sabemos cual es" — y Rodrigo colo una foto de Valsain
                // en Zarzalejo, a 32 km.
                val km = com.meteomontana.android.domain.util.Geo.haversineKm(
                    donde.lat, donde.lon, schoolLat, schoolLon)
                android.widget.Toast.makeText(
                    ctx,
                    "Esa foto se hizo a ${km.toInt()} km de $schoolName. " +
                        "Elige una foto tomada en esta escuela.",
                    android.widget.Toast.LENGTH_LONG).show()
            } else {
                photoSeed = com.meteomontana.android.ui.screens.detail.PhotoSeed(
                    photoUri = uri,
                    lat = donde.lat, lon = donde.lon,
                    aspect = donde.cameraDegrees?.let { com.meteomontana.android.domain.util.PhotoPlacement.aspectFromCameraDirection(it) })
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SchoolMap(
            centerLat     = schoolLat,
            centerLon     = schoolLon,
            blocks        = blocks,
            schoolName    = schoolName,
            schoolId      = schoolId,
            viewModel     = viewModel,
            photoSeed     = photoSeed,
            onPickBoulderFromPhoto = elegirFotoDePiedra,
            onPhotoSeedConsumed = { photoSeed = null },
            borrador = borradorPiedra,
            onGuardarBorrador = { d -> val con = d.copy(schoolId = schoolId); borradores.save(con); borradorPiedra = con },
            onBorrarBorrador = { borradores.clear(schoolId); borradorPiedra = null },
            onMyProposals = onMyProposals
        )
    }
}

@Composable
private fun BlockCard(b: Block, onClick: () -> Unit) {
    Column(modifier = Modifier
        .width(160.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        .padding(8.dp)
    ) {
        // Etiqueta del tipo
        Box(modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(typeColor(b.type), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(typeLabel(b.type), color = Color.White,
                style = MaterialTheme.typography.labelMedium)
        }
        if (!b.photoPath.isNullOrBlank()) {
            // Coil cargará el path (TODO: si es path-de-storage, pedir signed URL)
            AsyncImage(
                model = b.photoPath, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 6.dp)
            )
        }
        Text(b.name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1)
        if (b.lines.isNotEmpty()) {
            Text("${b.lines.size} ${if (b.lines.size == 1) "línea" else "líneas"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    "BLOCK"   -> "PIEDRA"
    "PARKING" -> "PARKING"
    "ZONE"    -> "ZONA"
    else      -> type
}

private fun typeColor(type: String) = when (type) {
    "BLOCK"   -> Color(0xFFC2410C)   // terra
    "PARKING" -> Color(0xFF5E6B4F)   // moss
    "ZONE"    -> Color(0xFFB45309)   // warn
    else      -> Color(0xFF5A574F)
}
