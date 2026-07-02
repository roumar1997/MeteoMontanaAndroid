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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R

@Composable
fun GripMeasureScreen(
    onBack: () -> Unit,
    viewModel: GripMeasureViewModel = hiltViewModel()
) {
    val gripTypes by viewModel.gripTypes.collectAsState()
    val selected by viewModel.selectedGripType.collectAsState()
    val hand by viewModel.hand.collectAsState()
    val points by viewModel.points.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val saved by viewModel.saved.collectAsState()

    // Pantalla siempre encendida mientras se mide (manos ocupadas con la báscula).
    KeepScreenOn()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Medir máximo", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (viewModel.connectedDeviceId == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Conecta primero tu báscula desde la pantalla anterior",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            items(gripTypes, key = { it.id }) { g ->
                val isSel = g.id == selected?.id
                Text(
                    g.label(),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(50)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable(enabled = phase is MeasurePhase.Idle) { viewModel.selectGripType(g) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LEFT" to "IZQUIERDA", "RIGHT" to "DERECHA").forEach { (value, label) ->
                val isSel = hand == value
                Text(
                    label,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(2.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable(enabled = phase is MeasurePhase.Idle) { viewModel.selectHand(value) }
                        .padding(vertical = 12.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GripLineChart(points = points, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        when (val p = phase) {
            MeasurePhase.Idle -> {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    Text("Pico: — kg", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                }
                Button(
                    onClick = { viewModel.startMeasuring() },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("EMPEZAR A TIRAR") }
            }
            MeasurePhase.Measuring -> {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    Text(
                        "Pico: ${points.maxOfOrNull { it.kg }?.let { "%.1f".format(it) } ?: "—"} kg",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Button(
                    onClick = { viewModel.stopMeasuring() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("PARAR") }
            }
            is MeasurePhase.Done -> {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pico: %.1f kg".format(p.peakKg), style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Media: %.1f kg · %ds".format(p.avgKg, p.durationS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saved) {
                    Text("GUARDADO", modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.reset() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) { Text("REPETIR", color = MaterialTheme.colorScheme.onBackground) }
                        Button(
                            onClick = { viewModel.save() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("GUARDAR") }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
