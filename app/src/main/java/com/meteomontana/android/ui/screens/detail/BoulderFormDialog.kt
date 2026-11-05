@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.CumbrePillShape
import com.meteomontana.android.ui.theme.terraFillColor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeChipColor
import kotlinx.coroutines.launch

// Formulario de "Nueva piedra" con sus caras (fotos), vías y opciones de muro.
// Reparto del antiguo ProposeContributionFlow.kt de 1.595 líneas.

@Composable
internal fun BoulderFormDialog(
    lat: Double,
    lon: Double,
    faces: List<BoulderFaceForm>,
    onFacesChange: (List<BoulderFaceForm>) -> Unit,
    onOpenTopo: (Int) -> Unit,
    sectorBlocks: List<Block>,
    sectorBlockId: String?,
    onSectorChange: (String?) -> Unit,
    discipline: String,
    onDisciplineChange: (String) -> Unit,
    /** F: orientación de la piedra según el autor (opcional; su primer voto). */
    blockOrientation: String? = null,
    onBlockOrientationChange: (String?) -> Unit = {},
    geometry: String,
    onGeometryChange: (String) -> Unit,
    direction: String,
    onDirectionChange: (String) -> Unit,
    path: List<Pair<Double, Double>>,
    onTraceWall: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: suspend () -> Boolean,
    onSaveOffline: (() -> Unit)? = null
) {
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val submitScope = rememberCoroutineScope()
    var selectedFaceIdx by remember { mutableStateOf(0) }
    val faceIdx = selectedFaceIdx.coerceIn(0, (faces.size - 1).coerceAtLeast(0))
    val face = faces.getOrNull(faceIdx) ?: BoulderFaceForm()
    /** Vía cuya ficha está ABIERTA (solo una a la vez); el resto se pliegan. */
    var expandedVia by remember { mutableStateOf<String?>(null) }
    // Cada cara nace con una vía vacía: si esa es la única de la cara, se abre
    // sola — tener que tocar "Sin nombre" para empezar sería un paso de más.
    androidx.compose.runtime.LaunchedEffect(faceIdx, face.bloques.size) {
        val unica = face.bloques.singleOrNull()
        if (unica != null && unica.name.isBlank() && unica.grade == null && unica.linePath.isEmpty()) {
            expandedVia = unica.id
        }
    }

    // Actualiza la cara seleccionada.
    fun updateFace(transform: (BoulderFaceForm) -> BoulderFaceForm) {
        onFacesChange(faces.toMutableList().also { it[faceIdx] = transform(it[faceIdx]) })
    }

    val ctxFoto = androidx.compose.ui.platform.LocalContext.current
    // El MISMO selector que en "aportar piedra desde una foto", para que elegir
    // foto se sienta igual en los dos sitios.
    val ponerFoto: (android.net.Uri) -> Unit = { uri ->
        run {
            // Si la foto guarda hacia donde apuntaba la camara, la orientacion
            // de la cara viene ya marcada: la pared mira al reves que la camara.
            // Es el mismo dato que usa "aportar piedra desde una foto"; no tiene
            // sentido pedirlo a mano cuando la foto ya lo trae.
            val sugerida = com.meteomontana.android.ui.components
                .readPhotoLocation(ctxFoto, uri)?.cameraDegrees
                ?.let { com.meteomontana.android.domain.util.PhotoPlacement
                    .aspectFromCameraDirection(it) }
            updateFace { cara ->
                cara.copy(photoUri = uri, orientation = cara.orientation ?: sugerida)
            }
            // Y la de la PIEDRA, que es el chip que se ve con una sola foto:
            // marcar solo la de la cara no lo enseñaba en ningún sitio.
            if (sugerida != null && blockOrientation == null) {
                onBlockOrientationChange(sugerida)
            }
        }
    }

    val elegirFoto = com.meteomontana.android.ui.components.rememberSelectorDeFoto(
        // Si la copia falla se avisa AHORA, no al guardar: así una cara nunca se
        // queda creyendo que tiene foto (era el camino por el que se perdían
        // fotos en silencio).
        onError = { error = AppText.get(R.string.boulder_form_dialog_v4_no_se_pudo_usar_esa) }
    ) { uri ->
        uri?.let(ponerFoto)   // null = salio del selector sin elegir: no hay nada que hacer
    }

    // Cámara además de galería, para CUALQUIER cara (1ª o siguientes) — antes
    // solo se podía elegir de galería aquí. La cámara no necesita el permiso
    // amplio de fotos que Play rechazó (esa política era solo sobre la
    // galería propia); usar TakePicture() + FileProvider no la toca
    // (Álvaro, 2026-08-29).
    var eligiendoOrigenFoto by remember { mutableStateOf(false) }
    var cameraUriPendiente by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraUriPendiente?.let(ponerFoto) }
    fun launchCamera() {
        val dir = java.io.File(ctxFoto.cacheDir, "nueva-piedra").apply { mkdirs() }
        val file = java.io.File(dir, "foto-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctxFoto, "${ctxFoto.packageName}.fileprovider", file
        )
        cameraUriPendiente = uri
        runCatching { cameraLauncher.launch(uri) }
    }

    // El envío se dispara desde la barra FIJA de arriba (y también desde el pie,
    // por si el usuario ya está al final del formulario).
    fun enviar() {
        sending = true; error = null
        submitScope.launch {
            val ok = onSubmit()
            sending = false
            if (!ok) error = AppText.get(R.string.boulder_form_dialog_v4_no_se_pudo_enviar_revisa)
        }
    }

    CumbreDialog(
        onDismiss = onCancel, scrollable = true, fullHeight = true,
        header = {
            SubmitHeader(
                title = stringResource(R.string.boulder_form_dialog_v2_nueva_piedra), sending = sending, error = error,
                onCancel = onCancel, onSubmit = { enviar() }
            )
        }
    ) {
        Text(stringResource(R.string.boulder_form_dialog_rellena_los_datos_de),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "boulder_form_guide",
            text = stringResource(R.string.boulder_form_dialog_v4_pasos_1_anade_una_foto)
        )
        Spacer(Modifier.height(Spacing.md))

        // ── Modalidad: BLOQUE o VÍA ───────────────────────────────────────────────
        Text(stringResource(R.string.propose_discipline), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.boulder_form_dialog_es_una_piedra_de),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        DisciplineSelector(selected = discipline, onSelect = onDisciplineChange)

        // F: orientación de la piedra (opcional). Al aprobarse será el PRIMER
        // voto del autor; la comunidad puede seguir votando después.
        Spacer(Modifier.height(Spacing.sm))
        OrientationPickRow(
            label = stringResource(R.string.boulder_form_dialog_orientacion_de_la_piedra),
            selected = blockOrientation,
            onPick = { a -> onBlockOrientationChange(if (blockOrientation == a) null else a) }
        )
        Spacer(Modifier.height(Spacing.md))

        BoulderAdvancedOptions(geometry, onGeometryChange, direction, onDirectionChange, path, onTraceWall,
            sectorBlocks, sectorBlockId, onSectorChange)
        Spacer(Modifier.height(Spacing.md))

        // ── Caras (fotos) ─────────────────────────────────────────────────────────
        // Una piedra grande no cabe en una foto: añade varias fotos, cada una con
        // sus vías. Pestañas para cambiar de foto; "+ AÑADIR FOTO" crea otra.
        // F: orientación por CARA (solo con varias fotos; con una vale la general).
        if (faces.size > 1) {
            OrientationPickRow(
                label = stringResource(R.string.boulder_form_dialog_orientacion_de_esta_cara),
                selected = face.orientation,
                onPick = { a -> updateFace { it.copy(orientation = if (it.orientation == a) null else a) } }
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(stringResource(R.string.boulder_form_dialog_v2_fotos_de_la_piedra), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            itemsIndexed(faces) { idx, _ ->
                val sel = idx == faceIdx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .then(if (sel) Modifier.background(terraFillColor()) else Modifier)
                        .border(1.dp, if (sel) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { selectedFaceIdx = idx }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(stringResource(R.string.boulder_form_dialog_v2_foto_1_s, idx + 1), style = EyebrowTextStyle,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable {
                            onFacesChange(faces + BoulderFaceForm())
                            selectedFaceIdx = faces.size  // selecciona la nueva
                        }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(stringResource(R.string.propose_add_photo), style = EyebrowTextStyle, color = Terra)
                }
            }
        }
        if (faces.size > 1) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // Reordenar la foto dentro del muro (cambia el orden global de vías).
                val canLeft = faceIdx > 0
                val canRight = faceIdx < faces.size - 1
                Text(stringResource(R.string.boulder_form_dialog_v2_mover_2), style = EyebrowTextStyle,
                    color = if (canLeft) Terra else MaterialTheme.colorScheme.outline,
                    modifier = if (canLeft) Modifier.clickable {
                        onFacesChange(faces.toMutableList().also {
                            val t = it[faceIdx - 1]; it[faceIdx - 1] = it[faceIdx]; it[faceIdx] = t
                        })
                        selectedFaceIdx = faceIdx - 1
                    } else Modifier)
                Text(stringResource(R.string.boulder_form_dialog_v2_mover), style = EyebrowTextStyle,
                    color = if (canRight) Terra else MaterialTheme.colorScheme.outline,
                    modifier = if (canRight) Modifier.clickable {
                        onFacesChange(faces.toMutableList().also {
                            val t = it[faceIdx + 1]; it[faceIdx + 1] = it[faceIdx]; it[faceIdx] = t
                        })
                        selectedFaceIdx = faceIdx + 1
                    } else Modifier)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.boulder_form_dialog_v2_quitar_esta_foto), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable {
                        val newFaces = faces.toMutableList().also { it.removeAt(faceIdx) }
                        selectedFaceIdx = (faceIdx - 1).coerceAtLeast(0)
                        onFacesChange(newFaces)
                    })
            }
        }
        Spacer(Modifier.height(Spacing.md))

        // ── Foto de la cara seleccionada ───────────────────────────────────────────
        val photoUri = face.photoUri
        if (photoUri != null) {
            Box {
                AsyncImage(
                    model = photoUri,
                    contentDescription = stringResource(R.string.boulder_form_dialog_v2_foto_1_s_de_la, faceIdx + 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { updateFace { it.copy(photoUri = null) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CumbrePillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CumbrePillShape)
                    .clickable { eligiendoOrigenFoto = true }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.boulder_form_dialog_v2_cambiar_foto), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { eligiendoOrigenFoto = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.boulder_form_dialog_v2_sin_foto_seleccionada),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.boulder_form_dialog_v2_seleccionar_foto), style = EyebrowTextStyle, color = Terra)
                }
            }
        }

        if (eligiendoOrigenFoto) {
            // AlertDialog NATIVO (mismo patrón que el resto de la app): siempre
            // pulsable en cualquier móvil.
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { eligiendoOrigenFoto = false },
                title = { Text(stringResource(R.string.boulder_form_dialog_como_quieres_la_foto)) },
                text = {
                    Column {
                        androidx.compose.material3.TextButton(
                            onClick = { eligiendoOrigenFoto = false; launchCamera() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.boulder_form_dialog_v2_hacer_foto_ahora), modifier = Modifier.fillMaxWidth()) }
                        androidx.compose.material3.TextButton(
                            onClick = { eligiendoOrigenFoto = false; elegirFoto() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.boulder_form_dialog_elegir_de_galeria), modifier = Modifier.fillMaxWidth()) }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { eligiendoOrigenFoto = false }) { Text(stringResource(R.string.boulder_form_dialog_v2_cancelar)) }
                }
            )
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Vías de esta foto ──────────────────────────────────────────────────────
        Text(stringResource(R.string.boulder_form_dialog_vias_en_esta_foto), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (geometry == "LINE") {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.boulder_form_dialog_numbering_note,
                if (direction == "LTR") stringResource(R.string.add_lines_direction_ltr_short) else stringResource(R.string.add_lines_direction_rtl_short)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        val isWall = geometry == "LINE"
        val totalVias = faces.sumOf { it.bloques.size }
        val precedingCount = faces.take(faceIdx).sumOf { it.bloques.size }
        face.bloques.forEachIndexed { idx, bloque ->
            val globalPos = precedingCount + idx
            val number = when {
                !isWall -> idx + 1
                direction == "LTR" -> globalPos + 1
                else -> totalVias - globalPos
            }
            // Una sola ficha ABIERTA a la vez, igual que al EDITAR: con los
            // formularios apilados, meter la quinta vía obligaba a scrollear
            // las cuatro anteriores. Paridad con BoulderFormSheet de iOS.
            if (bloque.id == expandedVia) {
                BloqueRow(
                    index = idx,
                    bloque = bloque,
                    displayNumber = number,
                    onUpdate = { updated ->
                        updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it[idx] = updated }) }
                    },
                    onDelete = if (face.bloques.size > 1) ({
                        updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it.removeAt(idx) }) }
                        expandedVia = null
                    }) else null,
                    onMoveUp = if (idx > 0) ({
                        updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also {
                            val t = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = t
                        }) }
                    }) else null,
                    onMoveDown = if (idx < face.bloques.size - 1) ({
                        updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also {
                            val t = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = t
                        }) }
                    }) else null
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).clip(CumbrePillShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CumbrePillShape)
                            .clickable { expandedVia = null }
                            .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.propose_ready), style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(CumbrePillShape)
                            .background(terraFillColor())
                            .clickable {
                                val nueva = BoulderBloqueForm()
                                updateFace { f -> f.copy(bloques = f.bloques + nueva) }
                                expandedVia = nueva.id
                            }
                            .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.via_anadir_otra), style = EyebrowTextStyle,
                            color = Color.White)
                    }
                }
            } else {
                ViaCompactaRow(
                    displayNumber = number,
                    bloque = bloque,
                    onOpen = { expandedVia = bloque.id },
                    onDelete = if (face.bloques.size > 1) ({
                        updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it.removeAt(idx) }) }
                    }) else null
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        // El botón sale si no hay una vía abierta EN ESTA FOTO (antes bastaba con que hubiera una abierta en otra).
        if (face.bloques.none { it.id == expandedVia }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CumbrePillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CumbrePillShape)
                    .clickable {
                        val nueva = BoulderBloqueForm()
                        updateFace { f -> f.copy(bloques = f.bloques + nueva) }
                        expandedVia = nueva.id
                    }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.boulder_form_dialog_anadir_via), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Dibujar líneas de esta foto ────────────────────────────────────────────
        Spacer(Modifier.height(Spacing.sm))
        val hasLines = face.bloques.any { it.linePath.isNotEmpty() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CumbrePillShape)
                .then(
                    if (photoUri != null) Modifier.background(terraFillColor())
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CumbrePillShape)
                )
                .clickable(enabled = photoUri != null) { if (photoUri != null) onOpenTopo(faceIdx) }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (hasLines) stringResource(R.string.boulder_form_dialog_v4_editar_lineas) else stringResource(R.string.boulder_form_dialog_v4_dibujar_lineas),
                style = EyebrowTextStyle,
                color = if (photoUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (photoUri == null) {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.boulder_form_dialog_anade_una_foto_para),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.lg))

        // ── Coordenadas ──────────────────────────────────────────────────────────
        Text(stringResource(R.string.boulder_form_dialog_v2_coordenadas_lat_lon), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.5f, %.5f".format(java.util.Locale.US, lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        Text(stringResource(R.string.boulder_form_dialog_posicion_desde_el_mapa), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(Spacing.md))

        // Enviar/Cancelar viven SOLO en la cabecera fija (SubmitHeader, arriba).
        // Aquí abajo había una segunda copia y salían los dos (Álvaro,
        // 2026-08-24: "sigue saliendo debajo lo de enviar"). Lo único que queda
        // es la salida offline, que no cabe en la cabecera y solo aparece si el
        // envío ha fallado.
        val errorActual = error
        if (errorActual != null && onSaveOffline != null) {
            SubmitFooterOffline(sending = sending, error = errorActual, onSaveOffline = onSaveOffline)
        }
    }
}

// ─── BloqueRow ────────────────────────────────────────────────────────────────

