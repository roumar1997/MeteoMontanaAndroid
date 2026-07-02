package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing

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
    val sessionPeak by viewModel.peakKg.collectAsState()

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
            Box(Modifier.fillMaxSize().padding(Spacing.xl), Alignment.Center) {
                Text("Conecta primero tu báscula desde la pantalla de Agarres",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
            return@Column
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg)
        ) {
            val editable = phase is MeasurePhase.Idle

            GripTypeTwoAxisSelector(
                gripTypes = gripTypes, selected = selected, enabled = editable,
                onSelect = { viewModel.selectGripType(it) }
            )

            Spacer(Modifier.height(Spacing.md))
            Text("MANO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs))
            HandSelector(hand = hand, enabled = editable, onSelect = { viewModel.selectHand(it) })

            Spacer(Modifier.height(Spacing.lg))

            // Número grande en vivo + gráfica.
            when (val p = phase) {
                MeasurePhase.Idle -> {
                    BigKgDisplay(kg = null, sublabel = "Elige agarre y mano, cuelga la báscula y dale a EMPEZAR")
                    GripLineChart(points = points, modifier = Modifier.padding(vertical = Spacing.md))
                    Button(
                        onClick = { viewModel.startMeasuring() },
                        enabled = selected != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("EMPEZAR A TIRAR", style = MaterialTheme.typography.labelLarge) }
                }
                MeasurePhase.Measuring -> {
                    val liveKg = points.lastOrNull()?.kg?.toDouble()
                    BigKgDisplay(
                        kg = liveKg,
                        sublabel = if (sessionPeak > 0) "Pico hasta ahora: %.1f kg".format(sessionPeak) else "Tira ya…"
                    )
                    GripLineChart(points = points, yMaxKg = sessionPeak.toFloat(),
                        modifier = Modifier.padding(vertical = Spacing.md))
                    Button(
                        onClick = { viewModel.stopMeasuring() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("PARAR", style = MaterialTheme.typography.labelLarge) }
                }
                is MeasurePhase.Done -> {
                    ResultCard(peakKg = p.peakKg, avgKg = p.avgKg, durationS = p.durationS,
                        gripLabel = selected?.let { "${fingerGroupLabel(it.fingerGroup)} · ${gripStyleLabel(it.style)}" } ?: "",
                        handText = if (hand == "LEFT") "Izquierda" else "Derecha")
                    GripLineChart(points = points, yMaxKg = p.peakKg.toFloat(),
                        modifier = Modifier.padding(vertical = Spacing.md))
                    if (saved) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                .padding(Spacing.md),
                            Alignment.Center
                        ) {
                            Text("✓ GUARDADO — si supera tu máximo anterior, queda como nuevo récord",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(2.dp)
                        ) { Text("MEDIR OTRA VEZ", color = MaterialTheme.colorScheme.onBackground) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(2.dp)
                            ) { Text("REPETIR", color = MaterialTheme.colorScheme.onBackground) }
                            Button(
                                onClick = { viewModel.save() },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text("GUARDAR") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** Número enorme en mono — protagonista absoluto de la pantalla. */
@Composable
private fun BigKgDisplay(kg: Double?, sublabel: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                kg?.let { "%.1f".format(it) } ?: "—",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 72.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(" kg", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md))
        }
        Text(sublabel, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResultCard(peakKg: Double, avgKg: Double, durationS: Int, gripLabel: String, handText: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RESULTADO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(peakKg),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 56.sp
                ),
                color = MaterialTheme.colorScheme.primary)
            Text(" kg", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm))
        }
        Text("$gripLabel · $handText", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Media %.1f kg · %ds de tirón".format(avgKg, durationS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
