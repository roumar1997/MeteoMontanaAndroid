package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import com.meteomontana.android.domain.grips.GripClimbGameEngine
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import kotlin.math.sin

@Composable
fun GripClimbGameScreen(
    onBack: () -> Unit,
    onGoMeasure: () -> Unit,
    viewModel: GripClimbGameViewModel = hiltViewModel()
) {
    val uiPhase by viewModel.uiPhase.collectAsState()

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Sube la pared", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (uiPhase) {
            ClimbGameUiPhase.Setup -> SetupBody(viewModel, onGoMeasure)
            ClimbGameUiPhase.NoMaxRecorded -> NoMaxBody(onGoMeasure, onBackToSetup = { viewModel.backToSetup() })
            ClimbGameUiPhase.Playing -> PlayingBody(viewModel)
        }
    }
}

@Composable
private fun SetupBody(viewModel: GripClimbGameViewModel, onGoMeasure: () -> Unit) {
    val gripTypes by viewModel.gripTypes.collectAsState()
    val selectedGripType by viewModel.selectedGripType.collectAsState()
    val hand by viewModel.hand.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val connected = viewModel.connectedDeviceId != null

    Column(Modifier.fillMaxSize().padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Cuelga del muro y sube tirando. Cuanto más alto, más se desploma — hasta que ya no sea calentamiento.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = Spacing.lg)
        )

        if (!connected) {
            Text("Conecta tu báscula primero desde la pantalla de Agarres.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = Spacing.lg))
        }

        Text("AGARRE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs))
        LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md)) {
            items(gripTypes, key = { it.id }) { g ->
                val isSel = g.id == selectedGripType?.id
                Text(
                    g.label(),
                    modifier = Modifier.padding(end = Spacing.sm)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable { viewModel.selectGripType(g) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Text("MANO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs))
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("LEFT" to "IZQUIERDA", "RIGHT" to "DERECHA").forEach { (value, label) ->
                val isSel = hand == value
                Text(
                    label,
                    modifier = Modifier.weight(1f)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { viewModel.selectHand(value) }
                        .padding(vertical = 12.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center
                )
            }
        }

        Text("DIFICULTAD", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs))
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xl), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                GripClimbGameEngine.Difficulty.FACIL to "FÁCIL",
                GripClimbGameEngine.Difficulty.MEDIO to "MEDIO",
                GripClimbGameEngine.Difficulty.DIFICIL to "DIFÍCIL"
            ).forEach { (value, label) ->
                val isSel = difficulty == value
                Text(
                    label,
                    modifier = Modifier.weight(1f)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { viewModel.selectDifficulty(value) }
                        .padding(vertical = 12.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center
                )
            }
        }

        Button(onClick = { viewModel.start() }, enabled = connected && selectedGripType != null,
            modifier = Modifier.fillMaxWidth()) {
            Text("EMPEZAR")
        }
    }
}

@Composable
private fun NoMaxBody(onGoMeasure: () -> Unit, onBackToSetup: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Spacing.xl), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Necesitas medir tu máximo con esta mano y agarre antes de jugar — así el juego sabe cuánto es \"tirar fuerte\" para ti.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = Spacing.lg)
            )
            Button(onClick = onGoMeasure, modifier = Modifier.fillMaxWidth()) { Text("IR A MEDIR MÁXIMO") }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onBackToSetup, modifier = Modifier.fillMaxWidth()) { Text("VOLVER") }
        }
    }
}

@Composable
private fun PlayingBody(viewModel: GripClimbGameViewModel) {
    val state by viewModel.engineState.collectAsState()
    val pct by viewModel.currentPct.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()

    val skyTop = Color(0xFFDCE9F0)
    val skyBottom = MaterialTheme.colorScheme.background
    val rockColor = Color(0xFF8A7A68)
    val rockShadow = Color(0xFF6E6152)
    val ropeColor = Color(0xFF3A3A38)
    val climberColor = MaterialTheme.colorScheme.primary
    val mountainColor = Color(0xFFB7C4B0)

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pxPerMeterY = 70f
            val climberScreenY = h * 0.42f
            val wallBaseX = w * 0.42f
            val maxLeanPx = w * 0.30f

            drawRect(color = skyTop, size = size)
            drawRect(color = skyBottom.copy(alpha = 0.25f), size = size)

            // Montañas de fondo, decorativas, ligero parallax con la altura.
            val parallax = ((state.heightM * 8.0) % (w * 2.0)).toFloat()
            val mountainPath = Path().apply {
                moveTo(-parallax, h * 0.55f)
                lineTo(w * 0.1f - parallax, h * 0.32f)
                lineTo(w * 0.3f - parallax, h * 0.5f)
                lineTo(w * 0.55f - parallax, h * 0.25f)
                lineTo(w * 0.8f - parallax, h * 0.48f)
                lineTo(w * 1.1f - parallax, h * 0.3f)
                lineTo(w * 1.3f - parallax, h * 0.55f)
                lineTo(w * 1.3f - parallax, h)
                lineTo(-parallax, h)
                close()
            }
            drawPath(mountainPath, color = mountainColor.copy(alpha = 0.5f))

            fun worldHeightAt(y: Float): Double =
                state.heightM + (climberScreenY - y) / pxPerMeterY
            fun leanFraction(heightM: Double): Float =
                (viewModel.overhangDegAt(heightM) / 75.0).coerceIn(0.0, 1.0).toFloat()
            fun wallXAt(y: Float): Float = wallBaseX + leanFraction(worldHeightAt(y)) * maxLeanPx

            // Silueta de la pared (roca sólida a la izquierda de la curva).
            val rockPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, 0f)
                var y = 0f
                val step = 10f
                while (y <= h) {
                    lineTo(wallXAt(y), y)
                    y += step
                }
                lineTo(wallXAt(h), h)
                close()
            }
            drawPath(rockPath, color = rockColor)
            // Textura simple: unas líneas de sombra diagonales.
            var ty = 0f
            while (ty <= h) {
                val x = wallXAt(ty)
                drawLine(rockShadow.copy(alpha = 0.35f), Offset(0f, ty), Offset(x, ty), strokeWidth = 1.5f)
                ty += 26f
            }

            // Posición del muñeco.
            val wallXNow = wallBaseX + leanFraction(state.heightM) * maxLeanPx
            val swinging = state.phase == GripClimbGameEngine.Phase.RAPPEL
            val ropeLenPx = ((state.ropePaidOutM + 1.2) * pxPerMeterY).toFloat()
            val swingOffsetX = if (swinging) (sin(state.swingAngleRad) * ropeLenPx).toFloat() else 0f
            val swingDropY = if (swinging) ((1.0 - kotlin.math.cos(state.swingAngleRad)) * ropeLenPx * 0.35).toFloat() else 0f
            val climberX = wallXNow + swingOffsetX
            val climberY = climberScreenY + swingDropY

            // Cuerda desde un punto de anclaje fijo por encima.
            val anchorY = climberScreenY - ropePxAnchor(pxPerMeterY)
            drawLine(ropeColor, Offset(wallXNow, anchorY), Offset(climberX, climberY), strokeWidth = 3f, cap = StrokeCap.Round)

            // Muñeco: cabeza + cuerpo simple.
            drawCircle(color = climberColor, radius = 12f, center = Offset(climberX, climberY - 16f))
            drawLine(climberColor, Offset(climberX, climberY - 4f), Offset(climberX, climberY + 22f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(climberColor, Offset(climberX, climberY), Offset(climberX - 14f, climberY + 10f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(climberColor, Offset(climberX, climberY), Offset(climberX + 14f, climberY - 8f), strokeWidth = 5f, cap = StrokeCap.Round)
        }

        // HUD
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HudStat("ALTURA", "%.1fm".format(state.heightM))
                HudStat("MEJOR", "%.1fm".format(state.bestHeightM))
                HudStat("DESPLOME", "${state.wallOverhangDeg.toInt()}°")
            }
            Spacer(Modifier.height(Spacing.sm))
            EffortBar(pct = pct, requiredPct = state.requiredPct)
        }

        if (state.phase == GripClimbGameEngine.Phase.GAME_OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡Rapelado!", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Altura máxima: %.1fm (${difficultyLabel(difficulty)})".format(state.bestHeightM),
                        style = MaterialTheme.typography.bodyLarge, color = Color.White,
                        modifier = Modifier.padding(vertical = Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { viewModel.retry() }) { Text("VOLVER A INTENTAR") }
                    }
                }
            }
        }
    }
}

private fun ropePxAnchor(pxPerMeterY: Float): Float = 1.2f * pxPerMeterY + 40f

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun EffortBar(pct: Double, requiredPct: Double) {
    val trackColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val fillColor = if (pct >= requiredPct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val markerColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier.fillMaxWidth().height(14.dp)
            .background(trackColor, RoundedCornerShape(2.dp))
            .border(1.dp, outlineColor, RoundedCornerShape(2.dp))
    ) {
        val fillFrac = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
        drawRect(color = fillColor, size = androidx.compose.ui.geometry.Size(size.width * fillFrac, size.height))
        // Marcador del % mínimo exigido ahora mismo por la pared.
        val reqFrac = (requiredPct / 100.0).coerceIn(0.0, 1.0).toFloat()
        val markerX = size.width * reqFrac
        drawLine(markerColor, Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth = 3f)
    }
}

private fun difficultyLabel(d: GripClimbGameEngine.Difficulty): String = when (d) {
    GripClimbGameEngine.Difficulty.FACIL -> "Fácil"
    GripClimbGameEngine.Difficulty.MEDIO -> "Medio"
    GripClimbGameEngine.Difficulty.DIFICIL -> "Difícil"
}
