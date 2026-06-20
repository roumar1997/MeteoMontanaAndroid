@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.components

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.data.api.dto.CreateBlockLineRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.ui.screens.detail.ContributionTopoDialog
import com.meteomontana.android.ui.screens.topo.LineStroke
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.screens.topo.toJson
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * Diálogo de edición de un bloque (admin).
 *
 * Permite cambiar nombre, descripción y coordenadas (manual o pegando lat,lon
 * de Google Maps). Para BLOCK también permite editar las líneas dibujadas
 * sobre la foto (abre el `ContributionTopoDialog` con los datos actuales).
 *
 * Al guardar dispara `onSave(updatedRequest)` que el caller envía via PUT
 * `/api/blocks/{id}`.
 *
 * Nota: para "mover por tap en el mapa" tras este dialog, el caller puede
 * llamar a `onMoveByMap()` y manejar el modo en su mapa.
 */
@Composable
fun EditBlockDialog(
    block: Block,
    onSave: (CreateBlockRequest) -> Unit,
    onMoveByMap: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(block.name) }
    var description by remember { mutableStateOf(block.description ?: "") }
    var latText by remember { mutableStateOf("%.6f".format(block.lat).replace(",", ".")) }
    var lonText by remember { mutableStateOf("%.6f".format(block.lon).replace(",", ".")) }
    var pasted by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // Para BLOCK: estado de las líneas editables + flag para abrir el editor topo
    val initialBloques = remember(block.id) {
        block.lines.map { line ->
            // Convierte enum del backend (STAND/SIT/JUMP/TRAV) al valor que
            // usa el editor (PIE/SIT/LANCE/TRAV) para que los chips marquen
            // bien la selección.
            val appStart = when (line.startType?.uppercase()) {
                "STAND" -> "PIE"
                "JUMP"  -> "LANCE"
                "SIT"   -> "SIT"
                "TRAV"  -> "TRAV"
                else    -> null
            }
            BoulderBloqueForm(
                name = line.name,
                grade = line.grade,
                startType = appStart,
                linePath = parseLineStroke(line.linePath).points
            )
        }.ifEmpty { listOf(BoulderBloqueForm()) }
    }
    var bloques by remember { mutableStateOf(initialBloques) }
    var showTopoEditor by remember { mutableStateOf(false) }
    // Modalidad de la piedra (BOULDER/ROUTE), editable solo para BLOCK.
    var discipline by remember { mutableStateOf(block.discipline) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            Text("Editar ${typeLabel(block.type)}",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.lg))

            // Nombre
            Text("NOMBRE", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(Modifier.height(Spacing.md))

            // Pegar coordenadas Google Maps
            Text("PEGAR COORDENADAS (GOOGLE MAPS)", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = pasted,
                onValueChange = { value ->
                    pasted = value
                    val parsed = parseLatLonPaste(value)
                    if (parsed != null) {
                        latText = "%.6f".format(parsed.first).replace(",", ".")
                        lonText = "%.6f".format(parsed.second).replace(",", ".")
                        pasted = ""
                    }
                },
                placeholder = { Text("Pega aquí ej: 40.4168, -3.7038",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            Spacer(Modifier.height(Spacing.sm))

            // Lat / Lon individuales
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.weight(1f)) {
                    Column {
                        Text("LATITUD", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = latText, onValueChange = { latText = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono)
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    Column {
                        Text("LONGITUD", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = lonText, onValueChange = { lonText = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono)
                        )
                    }
                }
            }

            // Botón "mover por tap en el mapa"
            if (onMoveByMap != null) {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, Terra, MaterialTheme.shapes.small)
                        .clickable(onClick = onMoveByMap)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍 MOVER PULSANDO EN EL MAPA", style = EyebrowTextStyle, color = Terra)
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // Descripción
            Text("DESCRIPCIÓN", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            // Para BLOCK: modalidad (bloque/vía)
            if (block.type == "BLOCK") {
                Spacer(Modifier.height(Spacing.md))
                Text("MODALIDAD", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                com.meteomontana.android.ui.screens.detail.DisciplineSelector(
                    selected = discipline,
                    onSelect = { discipline = it }
                )
            }

            // Para BLOCK: editor de líneas
            if (block.type == "BLOCK" && !block.photoPath.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.md))
                Text("VÍAS DIBUJADAS", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Text("${bloques.count { it.linePath.isNotEmpty() || it.name.isNotBlank() }} vías",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable { showTopoEditor = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎ EDITAR LÍNEAS Y VÍAS", style = EyebrowTextStyle, color = Color.White)
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // Footer
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(enabled = !saving, onClick = onDismiss)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCELAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier.weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable(enabled = !saving) {
                            val latD = latText.replace(",", ".").toDoubleOrNull()
                            val lonD = lonText.replace(",", ".").toDoubleOrNull()
                            if (latD != null && lonD != null && name.isNotBlank()) {
                                saving = true
                                onSave(buildUpdateRequest(
                                    block = block,
                                    name = name.trim(),
                                    lat = latD, lon = lonD,
                                    description = description.takeIf { it.isNotBlank() },
                                    bloques = bloques,
                                    discipline = discipline
                                ))
                            }
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    if (saving) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp
                    ) else Text("GUARDAR CAMBIOS", style = EyebrowTextStyle, color = Color.White)
                }
            }
        }
    }

    // Editor de líneas (precargado con líneas existentes)
    if (showTopoEditor && !block.photoPath.isNullOrBlank()) {
        ContributionTopoDialog(
            photoUri = Uri.parse(block.photoPath),
            bloques = bloques,
            onSave = { updated ->
                bloques = updated
                showTopoEditor = false
            },
            onDismiss = { showTopoEditor = false }
        )
    }
}

/** Parsea texto pegado de Google Maps. Mismo que en SubmitSchoolScreen. */
private fun parseLatLonPaste(raw: String): Pair<Double, Double>? {
    val matches = Regex("-?\\d+[\\.,]?\\d*").findAll(raw).map { it.value }.toList()
    if (matches.size < 2) return null
    val lat = matches[0].replace(",", ".").toDoubleOrNull() ?: return null
    val lon = matches[1].replace(",", ".").toDoubleOrNull() ?: return null
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
    return lat to lon
}

private fun typeLabel(type: String): String = when (type) {
    "PARKING" -> "parking"
    "ZONE"    -> "sector"
    else      -> "piedra"
}

/** Convierte BoulderBloqueForm + datos del bloque a CreateBlockRequest. */
private fun buildUpdateRequest(
    block: Block,
    name: String,
    lat: Double, lon: Double,
    description: String?,
    bloques: List<BoulderBloqueForm>,
    discipline: String
): CreateBlockRequest {
    val lines = if (block.type == "BLOCK") {
        bloques
            .filter { it.name.isNotBlank() || it.grade != null || it.linePath.isNotEmpty() }
            .map { b ->
                val startTypeBackend = when (b.startType?.uppercase()) {
                    "PIE", "STAND"      -> "STAND"
                    "SIT"               -> "SIT"
                    "LANCE", "JUMP"     -> "JUMP"
                    "TRAV"              -> "TRAV"
                    else                -> null
                }
                CreateBlockLineRequest(
                    name = b.name.ifBlank { "Sin nombre" },
                    grade = b.grade,
                    startType = startTypeBackend,
                    linePath = LineStroke(b.linePath).toJson()
                )
            }
    } else emptyList()

    return CreateBlockRequest(
        type = block.type,
        name = name,
        lat = lat, lon = lon,
        photoPath = block.photoPath,
        description = description,
        lines = lines,
        discipline = if (block.type == "BLOCK") discipline else null
    )
}
