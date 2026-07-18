package com.meteomontana.android.ui.screens.detail

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.toTopoLines
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.launch

/**
 * Flujo "✎ CORREGIR VÍA": modifica nombre/grado/tipo/posición de una vía
 * existente. Envía una contribución BOULDER con targetBlockId + targetLineId.
 *
 * Al abrir el editor topo, las vías existentes se muestran (la elegida resaltada
 * en su sitio actual, las demás como referencia). El usuario redibuja solo la
 * elegida sobre la foto. El admin verá la original y la propuesta superpuestas
 * para decidir.
 */
@Composable
fun EditLineFlow(
    block: Block,
    line: BlockLine,
    viewModel: SchoolDetailViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    // Cara (foto) a la que pertenece la vía: corregimos SOBRE ESA foto y con SUS
    // líneas como referencia, no mezclando las de otras caras de la piedra.
    val face = remember(block, line) {
        block.facesOrDerived().firstOrNull { f -> f.lines.any { it.id == line.id } }
    }
    val facePhoto = face?.photoPath ?: block.photoPath

    // Estado inicial con los valores actuales de la línea.
    var bloque by remember {
        mutableStateOf(
            BoulderBloqueForm(
                name = line.name,
                grade = line.grade,
                startType = startTypeForUi(line.startType?.toString()),
                linePath = parseLineStroke(line.linePath).points,
                facePhoto = facePhoto,
                // Arrastrar descripción y variante actuales: sin esto el form
                // salía vacío y al guardar podían perderse.
                description = line.lineDescription,
                variant = line.variant
            )
        )
    }
    var showTopo by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            Text("Corregir vía",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Estás proponiendo una corrección de \"${line.name}\" en \"${block.name}\". " +
                "Un admin revisará la propuesta antes de aplicar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))

            Text("DATOS DE LA VÍA", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))

            AddLineRow(
                displayNumber = 1,
                bloque = bloque,
                onUpdate = { bloque = it },
                onDelete = null
            )

            // Dibujar línea (solo si la cara tiene foto)
            if (!facePhoto.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.md))
                val hasLine = bloque.linePath.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable { showTopo = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (hasLine) "✎ EDITAR LÍNEA SOBRE LA FOTO"
                        else "✎ DIBUJAR LÍNEA SOBRE LA FOTO",
                        style = EyebrowTextStyle, color = Color.White
                    )
                }
            } else {
                Spacer(Modifier.height(Spacing.xs))
                Text("Esta piedra no tiene foto, no puedes redibujar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            error?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(enabled = !sending, onClick = onDismiss)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCELAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable(enabled = !sending) {
                            sending = true
                            error = null
                            scope.launch {
                                val result = viewModel.submitEditLineContribution(
                                    targetBlockId = block.id,
                                    targetLineId = line.id,
                                    targetLat = block.lat,
                                    targetLon = block.lon,
                                    bloque = bloque
                                )
                                if (result.isSuccess) onSuccess()
                                else {
                                    sending = false
                                    error = "No se pudo enviar la propuesta. Inténtalo de nuevo."
                                }
                            }
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp)
                    else Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
                }
            }
        }
    }

    // Editor topo: SOBRE la foto de la cara de la vía, y solo las vías de ESA
    // cara como referencia (las demás caras no se mezclan). El usuario redibuja
    // solo la elegida.
    if (showTopo && !facePhoto.isNullOrBlank()) {
        val otherLines: List<TopoLine> = (face?.lines ?: block.lines)
            .filter { it.id != line.id }
            .toTopoLines()
        ContributionTopoDialog(
            photoUri = Uri.parse(facePhoto),
            bloques = listOf(bloque),
            onSave = { updated ->
                bloque = updated.first()
                showTopo = false
            },
            onDismiss = { showTopo = false },
            existingLines = otherLines
        )
    }
}

/** STAND/SIT/JUMP/TRAV (backend) → PIE/SIT/LANCE/TRAV (app). */
private fun startTypeForUi(raw: String?): String? = when (raw?.uppercase()) {
    "STAND", "PIE" -> "PIE"
    "SIT"          -> "SIT"
    "SEMI"         -> "SEMI"
    "JUMP", "LANCE"-> "LANCE"
    "TRAV"         -> "TRAV"
    else           -> null
}
