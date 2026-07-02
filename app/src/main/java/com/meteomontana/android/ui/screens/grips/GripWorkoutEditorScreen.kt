package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun GripWorkoutEditorScreen(
    workoutId: String?,
    onBack: () -> Unit,
    onRun: (String) -> Unit,
    viewModel: GripWorkoutEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showMassEdit by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Editar entreno", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = state.name, onValueChange = viewModel::setName,
                    label = { Text("Nombre del entreno") },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg)
                )
            }
            item {
                Text("CAMBIO DE MANO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg))
                SegmentedRow(
                    options = listOf("UNA" to "Una/Ambas", "POR_SERIE" to "Por serie", "POR_REP" to "Por rep"),
                    selected = state.handMode, onSelect = viewModel::setHandMode
                )
            }
            item {
                Text("CONTAR POR", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                SegmentedRow(
                    options = listOf("TIEMPO" to "Tiempo", "PESO" to "Peso"),
                    selected = state.countMode, onSelect = viewModel::setCountMode
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SETS", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showMassEdit = true }) { Text("EDICIÓN MASIVA") }
                }
            }
            itemsIndexed(state.sets) { index, set ->
                SetCard(
                    index = index, set = set, countMode = state.countMode,
                    gripTypes = state.gripTypes,
                    onChange = { transform -> viewModel.updateSet(index, transform) },
                    onRemove = { viewModel.removeSet(index) },
                    removable = state.sets.size > 1
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        .clickable { viewModel.addSet() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("+ AGREGAR SET", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Descanso entre sets", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    NumberStepper(state.restBetweenSetsS, onChange = viewModel::setRestBetweenSets, step = 5, suffix = "s")
                }
            }
            item {
                val minutes = viewModel.estimatedDurationSeconds() / 60
                val seconds = viewModel.estimatedDurationSeconds() % 60
                Text(
                    "Duración estimada: ${minutes}m ${seconds}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    textAlign = TextAlign.Center
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Button(
            onClick = { viewModel.save { id -> onRun(id) } },
            enabled = state.name.isNotBlank() && state.sets.all { it.gripTypeId != null },
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("GUARDAR") }
    }

    if (showMassEdit) {
        var reps by remember { mutableStateOf(state.sets.firstOrNull()?.reps ?: 6) }
        var work by remember { mutableStateOf(state.sets.firstOrNull()?.workS ?: 10) }
        var rest by remember { mutableStateOf(state.sets.firstOrNull()?.restS ?: 20) }
        AlertDialog(
            onDismissRequest = { showMassEdit = false },
            title = { Text("Edición masiva") },
            text = {
                Column {
                    LabeledStepper("Reps", reps, { reps = it })
                    LabeledStepper("Trabajo (s)", work, { work = it }, step = 5)
                    LabeledStepper("Descanso (s)", rest, { rest = it }, step = 5)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applyToAllSets(reps, work, rest); showMassEdit = false }) {
                    Text("APLICAR A TODOS")
                }
            },
            dismissButton = { TextButton(onClick = { showMassEdit = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    list: List<EditableSet>,
    content: @Composable (Int, EditableSet) -> Unit
) {
    items(list.size) { index -> content(index, list[index]) }
}

@Composable
private fun SegmentedRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SetCard(
    index: Int, set: EditableSet, countMode: String,
    gripTypes: List<com.meteomontana.android.domain.model.GripType>,
    onChange: ((EditableSet) -> EditableSet) -> Unit,
    onRemove: () -> Unit, removable: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SET #${index + 1}", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Quitar set", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledStepper("Reps", set.reps, { onChange { s -> s.copy(reps = it) } }, modifier = Modifier.weight(1f))
            if (countMode == "TIEMPO") {
                LabeledStepper("Trabajo (s)", set.workS, { onChange { s -> s.copy(workS = it) } }, step = 5, modifier = Modifier.weight(1f))
            }
            LabeledStepper("Descanso (s)", set.restS, { onChange { s -> s.copy(restS = it) } }, step = 5, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.sm))
        GripTypeTwoAxisSelector(
            gripTypes = gripTypes,
            selected = gripTypes.firstOrNull { it.id == set.gripTypeId },
            onSelect = { g -> onChange { s -> s.copy(gripTypeId = g.id) } }
        )
        Spacer(Modifier.height(Spacing.sm))
        Text("RANGO OBJETIVO: ${set.targetMinPct.toInt()}% – ${set.targetMaxPct.toInt()}% de tu máximo",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.RangeSlider(
            value = set.targetMinPct..set.targetMaxPct,
            onValueChange = { range -> onChange { s -> s.copy(targetMinPct = range.start, targetMaxPct = range.endInclusive) } },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun LabeledStepper(label: String, value: Int, onChange: (Int) -> Unit, step: Int = 1, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NumberStepper(value, onChange, step)
    }
}

@Composable
private fun NumberStepper(value: Int, onChange: (Int) -> Unit, step: Int = 1, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) {
            Text("−", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("$value$suffix", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = { onChange(value + step) }) {
            Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
