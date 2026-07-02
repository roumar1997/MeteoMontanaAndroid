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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun GripsScreen(
    onConnect: () -> Unit = {},
    onMeasure: () -> Unit = {},
    onOpenWorkout: (String?) -> Unit = {},
    onProgress: () -> Unit = {},
    onClimbGame: () -> Unit = {},
    viewModel: GripsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val connection by GripScaleSession.connection.collectAsState()

    // Refresca al volver (p.ej. tras guardar un máximo en Medir o crear un
    // entreno) — si no, el dato nuevo no aparece hasta cambiar de tab.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera: título serif + estado de báscula a la derecha.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Agarres", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            ScaleStatusPill(connected = connection != null,
                label = connection?.alias ?: connection?.deviceId,
                onDisconnect = { GripScaleSession.clear() })
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
                            caption = if (connection != null) "Toca para cambiar" else "Busca tu WH-C06",
                            onClick = onConnect, modifier = Modifier.weight(1f),
                            highlighted = connection == null
                        )
                        ActionCard(
                            icon = Icons.Outlined.ShowChart, label = "MEDIR MÁXIMO",
                            caption = "Por agarre y mano",
                            onClick = onMeasure, modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                        ActionCard(
                            icon = Icons.Outlined.Terrain, label = "JUEGO: SUBE LA PARED",
                            caption = "Tira para escalar; suelta y rapelas",
                            onClick = onClimbGame, modifier = Modifier.fillMaxWidth(),
                            horizontal = true
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TUS MÁXIMOS", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.maxes.isNotEmpty()) {
                            Text("VER PROGRESO", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onProgress).padding(Spacing.xs))
                        }
                    }
                }
                item {
                    if (s.maxes.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(Spacing.lg)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                .padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Aún no has medido ningún máximo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "Conecta la báscula y haz tu primera prueba: elige agarre y mano, tira fuerte unos segundos y guarda.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Spacing.xs)
                            )
                            Text("EMPEZAR A MEDIR", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.md)
                                    .clickable(onClick = onMeasure).padding(Spacing.xs))
                        }
                    } else {
                        GripMaxesTable(
                            gripTypes = s.gripTypes, maxes = s.maxes,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        )
                    }
                }

                item { Spacer(Modifier.height(Spacing.md)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
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
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                .padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Aún no has creado ningún entreno",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("Define sets, reps, tiempos y el rango de fuerza objetivo (% de tu máximo).",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Spacing.xs))
                            Text("CREAR ENTRENO", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.md)
                                    .clickable { onOpenWorkout(null) }.padding(Spacing.xs))
                        }
                    }
                } else {
                    items(s.workouts, key = { it.id }) { workout ->
                        WorkoutCard(workout, onClick = { onOpenWorkout(workout.id) },
                            onDelete = { viewModel.deleteWorkout(workout.id) })
                    }
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun ScaleStatusPill(connected: Boolean, label: String?, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (connected) Color(0xFF1FA84E) else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            if (connected) (label ?: "Conectada") else "Sin báscula",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        if (connected) {
            Text("✕", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDisconnect).padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    horizontal: Boolean = false
) {
    val borderColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    if (horizontal) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(2.dp))
                .clickable(onClick = onClick)
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onBackground)
                Text(caption, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(2.dp))
                .clickable(onClick = onClick)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(Spacing.xs))
            Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)
            Text(caption, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun WorkoutCard(workout: GripWorkout, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
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
            Icon(Icons.Outlined.Delete, contentDescription = "Borrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun handModeLabel(mode: String): String = when (mode) {
    "UNA" -> "Una mano"
    "POR_SERIE" -> "Alterna por serie"
    "POR_REP" -> "Alterna por rep"
    else -> mode
}
