package com.meteomontana.android.ui.screens.topo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import androidx.compose.ui.graphics.PathEffect

private val GRADES = listOf("4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
    "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+")
// Mapeo compartido tipo de inicio → etiqueta (ui/components/StartTypeLabel.kt).
private val START_TYPE_KEYS = com.meteomontana.android.ui.components.START_TYPE_LABELS

/**
 * Editor topo. ViewModel carga el bloque por id via GET /api/blocks/{id}.
 */
@Composable
fun TopoEditorScreen(
    onBack: () -> Unit,
    viewModel: TopoEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val block = state.block

    var showCreateLineDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(block?.name ?: stringResource(R.string.topo_editor_default_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            if (block != null) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1A), contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.small
                ) { Text(if (state.saving) stringResource(R.string.topo_editor_saving) else stringResource(R.string.common_save)) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (block == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.error ?: stringResource(R.string.topo_editor_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // ───── Foto + Canvas líneas ─────
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

        Box(modifier = Modifier.fillMaxWidth().background(Color.Black)) {
            if (!block.photoPath.isNullOrBlank()) {
                AsyncImage(model = block.photoPath, contentDescription = null,
                    modifier = Modifier.fillMaxWidth())
            }
            // Capa Canvas de líneas
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .onGloballyPositionedSize { canvasSize = it }
                    .pointerInput(state.drawing, state.selectedLineId) {
                        if (state.drawing) {
                            detectTapGestures { tap ->
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    val norm = Offset(
                                        tap.x / canvasSize.width,
                                        tap.y / canvasSize.height
                                    )
                                    viewModel.addPointToSelected(norm)
                                }
                            }
                        }
                    }
            ) {
                state.lines.forEachIndexed { idx, line ->
                    if (line.stroke.points.isEmpty()) return@forEachIndexed
                    val style = gradeStyle(line.grade)
                    val path = Path()
                    line.stroke.points.forEachIndexed { i, p ->
                        val x = p.x * size.width
                        val y = p.y * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val isSelected = line.tempId == state.selectedLineId
                    drawPath(
                        path = path,
                        color = style.stroke,
                        style = Stroke(
                            width = if (isSelected) 8f else 5f,
                            pathEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(20f, 20f)) else null
                        )
                    )
                    // Punto inicio (en color del grado)
                    val first = line.stroke.points.first()
                    drawCircle(color = style.stroke,
                        radius = 12f,
                        center = Offset(first.x * size.width, first.y * size.height))
                    // Punto final con halo blanco
                    val last = line.stroke.points.last()
                    drawCircle(color = Color.White,
                        radius = 14f,
                        center = Offset(last.x * size.width, last.y * size.height))
                    drawCircle(color = style.stroke,
                        radius = 12f,
                        center = Offset(last.x * size.width, last.y * size.height))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ───── Toolbar dibujo ─────
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CumbreChip(
                label = if (state.drawing) stringResource(R.string.topo_editor_drawing) else stringResource(R.string.topo_editor_draw),
                selected = state.drawing,
                onClick = { viewModel.toggleDrawing() }
            )
            IconButton(onClick = { viewModel.undoLastPoint() }) {
                Icon(Icons.Outlined.Undo, contentDescription = stringResource(R.string.topo_editor_undo_point),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.weight(1f))
            CumbreChip(label = stringResource(R.string.topo_editor_add_line), selected = false,
                onClick = { showCreateLineDialog = true })
        }

        // ───── Lista de líneas ─────
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.topo_editor_lines_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (state.lines.isEmpty()) {
            Text(stringResource(R.string.topo_editor_empty_lines),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.lines) { line ->
                    LineChip(
                        line = line,
                        selected = line.tempId == state.selectedLineId,
                        onSelect = { viewModel.selectLine(line.tempId) },
                        onDelete = { viewModel.deleteLine(line.tempId) }
                    )
                }
            }
        }

        if (state.error != null) {
            Text(stringResource(R.string.topo_editor_error_prefix, state.error ?: ""),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showCreateLineDialog) {
        NewLineDialog(
            onDismiss = { showCreateLineDialog = false },
            onCreate = { name, grade, startType ->
                viewModel.startNewLine(name, grade, startType)
                showCreateLineDialog = false
            }
        )
    }
}

@Composable
private fun LineChip(
    line: EditableLine,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val color = colorForGrade(line.grade)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(2.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.width(12.dp).height(12.dp).clip(CircleShape).background(color))
        // Vía sin nombre → placeholder para que el chip sea visible y borrable.
        Text(line.name.ifBlank { "(sin datos)" },
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge)
        if (!line.grade.isNullOrBlank()) {
            Text(line.grade,
                color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
        }
        if (selected) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.topo_editor_delete),
                tint = Color.White,
                modifier = Modifier.clickable(onClick = onDelete).width(20.dp).height(20.dp))
        }
    }
}

@Composable
private fun NewLineDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf<String?>(null) }
    var startType by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.topo_editor_new_line_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.topo_editor_line_name_placeholder)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.topo_editor_grade_label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(GRADES) { g ->
                        CumbreChip(g, grade == g, { grade = g })
                    }
                }
                Text(stringResource(R.string.topo_editor_start_label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // FlowRow: con 5 tipos (SEMI incluido) una Row fija se desborda
                // del diálogo y los chips salían cortados.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    START_TYPE_KEYS.forEach { (v, labelRes) ->
                        CumbreChip(stringResource(labelRes), startType == v, { startType = v })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, grade, startType) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.topo_editor_create)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

// Helper: captura el tamaño del Canvas usando onSizeChanged de Compose
private fun Modifier.onGloballyPositionedSize(
    onSize: (IntSize) -> Unit
): Modifier = this.onSizeChanged { onSize(it) }
