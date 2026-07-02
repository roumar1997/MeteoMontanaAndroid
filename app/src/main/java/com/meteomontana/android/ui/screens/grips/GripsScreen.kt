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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun GripsScreen(
    onConnect: () -> Unit = {},
    onMeasure: () -> Unit = {},
    onOpenWorkout: (String?) -> Unit = {},
    onProgress: () -> Unit = {},
    viewModel: GripsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val connection by GripScaleSession.connection.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Agarres", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        // Estado de conexión de la báscula, siempre visible.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (connection != null) Color(0xFF1FA84E) else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Text(
                if (connection != null) "Báscula conectada · ${connection?.alias ?: connection?.deviceId}"
                else "Ninguna báscula conectada",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (connection != null) {
                TextButton(onClick = { GripScaleSession.clear() }) { Text("DESCONECTAR") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            GripsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is GripsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is GripsUiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        ActionCard(
                            icon = if (connection != null) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth,
                            label = if (connection != null) "BÁSCULA CONECTADA" else "CONECTAR BÁSCULA",
                            onClick = onConnect, modifier = Modifier
                        )
                        ActionCard(
                            icon = Icons.Outlined.ShowChart, label = "MEDIR MÁXIMO",
                            onClick = onMeasure, modifier = Modifier
                        )
                    }
                }
                if (s.maxes.isNotEmpty()) {
                    item {
                        Text("TUS MÁXIMOS", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                            s.maxes.groupBy { it.gripTypeId }.forEach { (gripTypeId, records) ->
                                val gripType = s.gripTypes.firstOrNull { it.id == gripTypeId }
                                if (gripType != null) {
                                    val left = records.firstOrNull { it.hand == "LEFT" }
                                    val right = records.firstOrNull { it.hand == "RIGHT" }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(gripType.label(), style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground)
                                        Text(
                                            "IZQ ${left?.maxKg?.let { "%.1f".format(it) } ?: "—"}kg · " +
                                                "DER ${right?.maxKg?.let { "%.1f".format(it) } ?: "—"}kg",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(onClick = onProgress)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("VER PROGRESO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TUS ENTRENOS", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { onOpenWorkout(null) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Nuevo entreno",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (s.workouts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.xl), Alignment.Center) {
                            Text("Aún no has creado ningún entreno",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(s.workouts, key = { it.id }) { workout ->
                        WorkoutRow(workout, onClick = { onOpenWorkout(workout.id) },
                            onDelete = { viewModel.deleteWorkout(workout.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(28.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun WorkoutRow(workout: GripWorkout, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(workout.name, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text("${workout.sets.size} sets · ${handModeLabel(workout.handMode)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun handModeLabel(mode: String): String = when (mode) {
    "UNA" -> "Una mano"
    "POR_SERIE" -> "Alterna por serie"
    "POR_REP" -> "Alterna por rep"
    else -> mode
}
