@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    // Muro/sector plegados: abiertos solo si ya son relevantes.
    var advancedOpen by remember { mutableStateOf(geometry == "LINE" || sectorBlockId != null) }
    var sectorExpanded by remember { mutableStateOf(false) }
    var selectedFaceIdx by remember { mutableStateOf(0) }
    val faceIdx = selectedFaceIdx.coerceIn(0, (faces.size - 1).coerceAtLeast(0))
    val face = faces.getOrNull(faceIdx) ?: BoulderFaceForm()

    // Actualiza la cara seleccionada.
    fun updateFace(transform: (BoulderFaceForm) -> BoulderFaceForm) {
        onFacesChange(faces.toMutableList().also { it[faceIdx] = transform(it[faceIdx]) })
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) updateFace { it.copy(photoUri = uri) } }

    CumbreDialog(onDismiss = onCancel, scrollable = true, fullHeight = true) {
        Text("Nueva piedra",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text("Rellena los datos de la piedra. Podrás añadir fotos y dibujar las líneas de cada vía sobre ellas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "boulder_form_guide",
            text = "Pasos: 1) Añade una foto de la piedra, 2) marca sus vías con grado, 3) dibuja las líneas sobre la foto, 4) envía."
        )
        Spacer(Modifier.height(Spacing.md))

        // ── Modalidad: BLOQUE o VÍA ───────────────────────────────────────────────
        Text(stringResource(R.string.propose_discipline), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("¿Es una piedra de boulder (sentadas, bloques cortos) o de vía (escalada deportiva, más larga)?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        DisciplineSelector(selected = discipline, onSelect = onDisciplineChange)
        Spacer(Modifier.height(Spacing.md))

        // ── Opciones avanzadas: muro, sector y numeración (plegado) ─────────────
        // El 90% de las propuestas son una piedra normal: geometría de muro,
        // sentido de numeración y sector viven plegados para no estorbar. Se
        // abre solo si ya son relevantes (muro trazado o sector elegido).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, if (advancedOpen) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { advancedOpen = !advancedOpen }
                .padding(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Muro, sector y numeración",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (geometry == "LINE") "Muro de ${path.size} puntos" +
                            (sectorBlockId?.let { " · con sector" } ?: "")
                         else if (sectorBlockId != null) "Con sector asignado"
                         else "Solo si es una pared larga o va en un sector",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (advancedOpen) "▲" else "▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (advancedOpen) {
            Spacer(Modifier.height(Spacing.sm))
            // ── Geometría: PUNTO o MURO ───────────────────────────────────────────────
            Text(stringResource(R.string.propose_geometry), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text("Punto = una piedra suelta. Muro = una pared larga que se traza en el mapa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            GeometrySelector(selected = geometry, onSelect = onGeometryChange)
            if (geometry == "LINE") {
                Spacer(Modifier.height(Spacing.xs))
                val traced = path.size >= 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (traced) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            else Modifier.background(Terra)
                        )
                        .clickable(onClick = onTraceWall)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            traced -> "✓ MURO DE ${path.size} PUNTOS · RE-TRAZAR"
                            path.size == 1 -> "TRAZAR EL MURO (1 PUNTO, FALTAN MÁS)"
                            else -> "✎ TRAZAR EL MURO EN EL MAPA"
                        },
                        style = EyebrowTextStyle,
                        color = if (traced) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                }
                if (!traced) {
                    Spacer(Modifier.height(2.dp))
                    Text("Toca al menos 2 puntos siguiendo la base del muro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.propose_direction), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                DirectionSelector(selected = direction, onSelect = onDirectionChange)
            }
            Spacer(Modifier.height(Spacing.md))
            // ── Sector (opcional) ────────────────────────────────────────────────────
            if (sectorBlocks.isNotEmpty()) {
                Text("SECTOR (OPCIONAL)", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                val selectedSectorName = sectorBlocks.firstOrNull { it.id == sectorBlockId }?.name
                ExposedDropdownMenuBox(
                    expanded = sectorExpanded,
                    onExpandedChange = { sectorExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSectorName ?: "Sin sector",
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectorExpanded)
                        },
                        shape = MaterialTheme.shapes.small, colors = fieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = sectorExpanded,
                        onDismissRequest = { sectorExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin sector",
                                style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onSectorChange(null); sectorExpanded = false }
                        )
                        sectorBlocks.forEach { sect ->
                            DropdownMenuItem(
                                text = { Text(sect.name,
                                    style = MaterialTheme.typography.bodyMedium) },
                                onClick = { onSectorChange(sect.id); sectorExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
        Spacer(Modifier.height(Spacing.md))

        // ── Caras (fotos) ─────────────────────────────────────────────────────────
        // Una piedra grande no cabe en una foto: añade varias fotos, cada una con
        // sus vías. Pestañas para cambiar de foto; "+ AÑADIR FOTO" crea otra.
        Text("FOTOS DE LA PIEDRA", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            itemsIndexed(faces) { idx, _ ->
                val sel = idx == faceIdx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .then(if (sel) Modifier.background(Terra) else Modifier)
                        .border(1.dp, if (sel) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { selectedFaceIdx = idx }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text("FOTO ${idx + 1}", style = EyebrowTextStyle,
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
                Text("◀ MOVER", style = EyebrowTextStyle,
                    color = if (canLeft) Terra else MaterialTheme.colorScheme.outline,
                    modifier = if (canLeft) Modifier.clickable {
                        onFacesChange(faces.toMutableList().also {
                            val t = it[faceIdx - 1]; it[faceIdx - 1] = it[faceIdx]; it[faceIdx] = t
                        })
                        selectedFaceIdx = faceIdx - 1
                    } else Modifier)
                Text("MOVER ▶", style = EyebrowTextStyle,
                    color = if (canRight) Terra else MaterialTheme.colorScheme.outline,
                    modifier = if (canRight) Modifier.clickable {
                        onFacesChange(faces.toMutableList().also {
                            val t = it[faceIdx + 1]; it[faceIdx + 1] = it[faceIdx]; it[faceIdx] = t
                        })
                        selectedFaceIdx = faceIdx + 1
                    } else Modifier)
                Spacer(Modifier.weight(1f))
                Text("✕ QUITAR ESTA FOTO", style = EyebrowTextStyle,
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
                    contentDescription = "Foto ${faceIdx + 1} de la piedra",
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
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { photoLauncher.launch("image/*") }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("CAMBIAR FOTO", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin foto seleccionada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("SELECCIONAR FOTO", style = EyebrowTextStyle, color = Terra)
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Vías de esta foto ──────────────────────────────────────────────────────
        Text("VÍAS EN ESTA FOTO", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (geometry == "LINE") {
            Spacer(Modifier.height(2.dp))
            Text("Nº = posición en el muro (${if (direction == "LTR") "izq→der" else "der→izq"}). Reordena con ▲▼.",
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
            BloqueRow(
                index = idx,
                bloque = bloque,
                displayNumber = number,
                onUpdate = { updated ->
                    updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it[idx] = updated }) }
                },
                onDelete = if (face.bloques.size > 1) ({
                    updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it.removeAt(idx) }) }
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
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable { updateFace { f -> f.copy(bloques = f.bloques + BoulderBloqueForm()) } }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text("+ AÑADIR VÍA", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Dibujar líneas de esta foto ────────────────────────────────────────────
        Spacer(Modifier.height(Spacing.sm))
        val hasLines = face.bloques.any { it.linePath.isNotEmpty() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (photoUri != null) Modifier.background(Terra)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                )
                .clickable(enabled = photoUri != null) { if (photoUri != null) onOpenTopo(faceIdx) }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (hasLines) "✎ EDITAR LÍNEAS" else "✎ DIBUJAR LÍNEAS",
                style = EyebrowTextStyle,
                color = if (photoUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (photoUri == null) {
            Spacer(Modifier.height(2.dp))
            Text("Añade una foto para poder dibujar las líneas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.lg))

        // ── Coordenadas ──────────────────────────────────────────────────────────
        Text("COORDENADAS (LAT, LON)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.6f, %.6f".format(lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        Text("✓ POSICIÓN DESDE EL MAPA", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(Spacing.md))

        // ── Footer ────────────────────────────────────────────────────────────────
        SubmitFooter(
            sending = sending, error = error,
            onCancel = onCancel,
            onSubmit = {
                sending = true; error = null
                submitScope.launch {
                    val ok = onSubmit()
                    sending = false
                    if (!ok) error = "No se pudo enviar. Revisa la conexión — la foto y las vías siguen aquí."
                }
            },
            onSaveOffline = onSaveOffline
        )
    }
}

// ─── BloqueRow ────────────────────────────────────────────────────────────────

@Composable
private fun BloqueRow(
    index: Int,
    bloque: BoulderBloqueForm,
    onUpdate: (BoulderBloqueForm) -> Unit,
    onDelete: (() -> Unit)?,
    // Número mostrado en el círculo. En muro = nº global en vivo (orden + dirección).
    displayNumber: Int = index + 1,
    // Tirador para reordenar la vía (null = extremo, deshabilitado).
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val gradeColor = colorForGrade(bloque.grade)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Tirador ▲▼ + número + nombre + eliminar
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Tirador para reordenar (subir/bajar) — visible solo si hay dónde mover.
            if (onMoveUp != null || onMoveDown != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▲", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveUp != null) Terra
                                else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveUp != null) Modifier.clickable(onClick = onMoveUp) else Modifier)
                    Text("▼", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveDown != null) Terra
                                else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveDown != null) Modifier.clickable(onClick = onMoveDown) else Modifier)
                }
            }
            // Número del bloque (en muro = nº global en vivo)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(gradeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("$displayNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White)
            }
            // Nombre
            OutlinedTextField(
                value = bloque.name,
                onValueChange = { onUpdate(bloque.copy(name = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nombre (opcional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            // Eliminar bloque
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text("✕", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Grado: grid de chips de un toque (colores por dificultad).
        Text("Grado", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradeChipsGrid(selected = bloque.grade,
            onSelect = { onUpdate(bloque.copy(grade = it)) })

        // Tipo de inicio con nombre completo.
        Text("Tipo de inicio", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StartTypeChips(selected = bloque.startType,
            onSelect = { onUpdate(bloque.copy(startType = it)) })

        // Variante opcional: distingue vías homónimas ("directa", "extensión").
        OutlinedTextField(
            value = bloque.variant ?: "",
            onValueChange = { if (it.length <= 60) onUpdate(bloque.copy(variant = it.takeIf { t -> t.isNotBlank() })) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.line_variant_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        Text(
            stringResource(R.string.line_variant_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Descripción opcional (beta, salida, detalle) — paridad con el editor
        // de vías (AddLineRow). Faltaba SOLO al CREAR piedra; ahora coinciden.
        OutlinedTextField(
            value = bloque.description ?: "",
            onValueChange = { onUpdate(bloque.copy(description = it.takeIf { t -> t.isNotBlank() })) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Descripción (opcional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            maxLines = 3,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        // Resumen de línea dibujada si existe
        if (bloque.linePath.isNotEmpty()) {
            Text(
                "✓ Línea dibujada (${bloque.linePath.size} puntos)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
