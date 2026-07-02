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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.grips.GripClimbGameEngine
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing
import kotlin.math.cos
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

    when (uiPhase) {
        ClimbGameUiPhase.Setup -> SetupBody(viewModel, onBack)
        ClimbGameUiPhase.NoMaxRecorded -> NoMaxBody(onGoMeasure, onBackToSetup = { viewModel.backToSetup() })
        // Jugando: el lienzo ocupa TODA la pantalla (sin cabecera), con una ✕
        // flotante para salir. Cuanto más espacio, mejor se juega.
        ClimbGameUiPhase.Playing -> PlayingBody(viewModel, onExit = { viewModel.backToSetup() })
    }
}

// =============================================================================
// Setup
// =============================================================================

@Composable
private fun SetupBody(viewModel: GripClimbGameViewModel, onBack: () -> Unit) {
    val gripTypes by viewModel.gripTypes.collectAsState()
    val selectedGripType by viewModel.selectedGripType.collectAsState()
    val hand by viewModel.hand.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val connected = viewModel.connectedDeviceId != null

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Sube la pared", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg)
        ) {
            Text(
                "Tira de la báscula para escalar. Suelta y rapelas hacia atrás en péndulo. " +
                    "Cuanto más alto, más se tumba la pared — y más fuerza te pide.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.lg)
            )

            if (!connected) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                        .padding(Spacing.md)
                ) {
                    Text("Conecta tu báscula primero desde la pantalla de Agarres.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            GripTypeTwoAxisSelector(
                gripTypes = gripTypes, selected = selectedGripType,
                onSelect = { viewModel.selectGripType(it) }
            )

            Spacer(Modifier.height(Spacing.md))
            Text("MANO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs))
            HandSelector(hand = hand, onSelect = { viewModel.selectHand(it) })

            Spacer(Modifier.height(Spacing.md))
            Text("DIFICULTAD", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    Triple(GripClimbGameEngine.Difficulty.FACIL, "FÁCIL", "Se tumba despacio · te pedirá hasta el 55% de tu máximo"),
                    Triple(GripClimbGameEngine.Difficulty.MEDIO, "MEDIO", "Ritmo de calentamiento serio · hasta el 75%"),
                    Triple(GripClimbGameEngine.Difficulty.DIFICIL, "DIFÍCIL", "Desploma rápido · hasta el 92% — esto ya no es calentar")
                ).forEach { (value, label, desc) ->
                    val isSel = difficulty == value
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { viewModel.selectDifficulty(value) }
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = EyebrowTextStyle,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                            Text(desc, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        if (isSel) Text("●", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = { viewModel.start() },
                enabled = connected && selectedGripType != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("EMPEZAR", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun NoMaxBody(onGoMeasure: () -> Unit, onBackToSetup: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.xl), Alignment.Center) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Falta tu máximo con esta mano y agarre",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center
            )
            Text(
                "El juego mide el esfuerzo como % de TU máximo, así las dos manos exigen lo mismo. Mídelo una vez y listo.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = Spacing.md)
            )
            Button(onClick = onGoMeasure, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(2.dp)) { Text("IR A MEDIR MÁXIMO") }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onBackToSetup, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(2.dp)) {
                Text("VOLVER", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

// =============================================================================
// Juego
// =============================================================================

@Composable
private fun PlayingBody(viewModel: GripClimbGameViewModel, onExit: () -> Unit) {
    val state by viewModel.engineState.collectAsState()
    val pct by viewModel.currentPct.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val hand by viewModel.hand.collectAsState()

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val skyTop = if (isDark) Color(0xFF1B2733) else Color(0xFFC9DCE8)
    val skyBottom = if (isDark) Color(0xFF15140F) else Color(0xFFF0EAD8)
    val rockColor = if (isDark) Color(0xFF4A4238) else Color(0xFF8A7A68)
    val rockEdge = if (isDark) Color(0xFF5C5346) else Color(0xFF6E6152)
    val ropeColor = if (isDark) Color(0xFFD8D2C2) else Color(0xFF3A3A38)
    val climberColor = MaterialTheme.colorScheme.primary
    val mountainFar = if (isDark) Color(0xFF2A3540) else Color(0xFFA9BCC7)
    val mountainNear = if (isDark) Color(0xFF33402E) else Color(0xFFB7C4B0)

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pxPerMeterY = 70f
            val climberScreenY = h * 0.46f
            val wallBaseX = w * 0.38f
            val maxLeanPx = w * 0.34f

            // Cielo con gradiente.
            drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom)), size = size)

            // Dos capas de montañas con parallax distinto (profundidad).
            fun mountains(offsetFactor: Float, baseY: Float, peakY: Float, color: Color) {
                val parallax = ((state.heightM * 60.0 * offsetFactor) % (w * 2.0)).toFloat()
                val path = Path().apply {
                    moveTo(-parallax, baseY)
                    lineTo(w * 0.15f - parallax, peakY)
                    lineTo(w * 0.35f - parallax, baseY * 0.94f)
                    lineTo(w * 0.6f - parallax, peakY * 0.85f)
                    lineTo(w * 0.85f - parallax, baseY * 0.96f)
                    lineTo(w * 1.15f - parallax, peakY)
                    lineTo(w * 1.4f - parallax, baseY * 0.92f)
                    lineTo(w * 1.7f - parallax, peakY * 0.9f)
                    lineTo(w * 2f - parallax, baseY)
                    lineTo(w * 2f - parallax, h)
                    lineTo(-parallax, h)
                    close()
                }
                drawPath(path, color = color)
            }
            mountains(offsetFactor = 0.4f, baseY = h * 0.6f, peakY = h * 0.34f, color = mountainFar.copy(alpha = 0.55f))
            mountains(offsetFactor = 1.0f, baseY = h * 0.72f, peakY = h * 0.46f, color = mountainNear.copy(alpha = 0.55f))

            // ---- La pared. Perfil calculado punto a punto: se tumba de verdad. ----
            fun worldHeightAt(y: Float): Double = state.heightM + (climberScreenY - y) / pxPerMeterY
            fun leanFraction(heightM: Double): Float =
                (viewModel.overhangDegAt(heightM) / 75.0).coerceIn(0.0, 1.0).toFloat()
            fun wallXAt(y: Float): Float = wallBaseX + leanFraction(worldHeightAt(y)) * maxLeanPx

            val rockPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, 0f)
                var y = 0f
                while (y <= h) { lineTo(wallXAt(y), y); y += 8f }
                lineTo(wallXAt(h), h)
                close()
            }
            drawPath(rockPath, color = rockColor)
            // Borde de la pared, marcado.
            var ey = 0f
            var prev = Offset(wallXAt(0f), 0f)
            while (ey <= h) {
                val p = Offset(wallXAt(ey), ey)
                drawLine(rockEdge, prev, p, strokeWidth = 4f)
                prev = p
                ey += 8f
            }
            // Vetas de la roca ANCLADAS AL MUNDO (cada 0,55m de pared): al
            // escalar se desplazan hacia abajo contigo — sin esto la pared
            // parecía estática aunque subieras.
            var veinH = kotlin.math.floor(worldHeightAt(h) / 0.55) * 0.55
            while (true) {
                val vy = climberScreenY - ((veinH - state.heightM) * pxPerMeterY).toFloat()
                if (vy < -10f) break
                if (vy in 0f..h) {
                    val x = wallXAt(vy)
                    // Longitud pseudo-aleatoria estable por posición del mundo.
                    val n = (veinH * 100).toInt()
                    val len = 0.35f + ((n * 31) % 40) / 100f
                    drawLine(rockEdge.copy(alpha = 0.30f), Offset(x * (1f - len), vy), Offset(x - 8f, vy), strokeWidth = 1.5f)
                }
                veinH += 0.55
            }
            // PRESAS sobre la pared (cada ~0,8m, offset pseudo-aleatorio
            // estable) — refuerzan la sensación de movimiento al escalar.
            var holdH = kotlin.math.floor(worldHeightAt(h) / 0.8) * 0.8
            while (true) {
                val hy = climberScreenY - ((holdH - state.heightM) * pxPerMeterY).toFloat()
                if (hy < -10f) break
                if (hy in 0f..h && holdH > 0.3) {
                    val x = wallXAt(hy)
                    val n = (holdH * 100).toInt()
                    val offset = 14f + ((n * 17) % 46)
                    val r = 5f + ((n * 13) % 5)
                    drawCircle(rockEdge, radius = r, center = Offset(x - offset, hy))
                    drawCircle(rockColor, radius = r - 2.5f, center = Offset(x - offset, hy - 1.5f))
                }
                holdH += 0.8
            }
            // Marcas de altura cada 5m sobre la pared.
            val firstMark = ((worldHeightAt(h).toInt() / 5) * 5).coerceAtLeast(0)
            var mark = firstMark
            while (true) {
                val markY = climberScreenY - ((mark - state.heightM) * pxPerMeterY).toFloat()
                if (markY < -20f) break
                if (markY in 0f..h && mark > 0) {
                    val x = wallXAt(markY)
                    drawLine(rockEdge.copy(alpha = 0.8f), Offset(x - 26f, markY), Offset(x - 6f, markY), strokeWidth = 3f)
                }
                mark += 5
            }

            // ---- Muñeco + cuerda (péndulo real al rapelar). ----
            val wallXNow = wallBaseX + leanFraction(state.heightM) * maxLeanPx
            val swinging = state.phase == GripClimbGameEngine.Phase.RAPPEL
            val ropeLenPx = ((state.ropePaidOutM + 1.2) * pxPerMeterY).toFloat()
            val anchorX = wallXNow
            val anchorY = climberScreenY - ropeLenPx * 0.9f
            val climberX = if (swinging) anchorX + (sin(state.swingAngleRad) * ropeLenPx).toFloat() else wallXNow + 6f
            val climberY = if (swinging) anchorY + (cos(state.swingAngleRad) * ropeLenPx).toFloat() else climberScreenY

            // Anclaje (chapa) + cuerda.
            drawCircle(color = rockEdge, radius = 7f, center = Offset(anchorX, anchorY))
            drawCircle(color = skyTop, radius = 3f, center = Offset(anchorX, anchorY))
            drawLine(ropeColor, Offset(anchorX, anchorY), Offset(climberX, climberY - 14f),
                strokeWidth = 3.5f, cap = StrokeCap.Round)

            // Muñeco algo más grande: cabeza, tronco, brazos, piernas.
            val bodyC = climberColor
            drawCircle(color = bodyC, radius = 13f, center = Offset(climberX, climberY - 22f))
            drawLine(bodyC, Offset(climberX, climberY - 9f), Offset(climberX, climberY + 20f), strokeWidth = 7f, cap = StrokeCap.Round)
            if (swinging) {
                // Rapelando: brazos arriba a la cuerda, piernas por delante.
                drawLine(bodyC, Offset(climberX, climberY - 5f), Offset(climberX - 6f, climberY - 20f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(bodyC, Offset(climberX, climberY - 5f), Offset(climberX + 6f, climberY - 20f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX - 16f, climberY + 26f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX - 12f, climberY + 34f), strokeWidth = 5f, cap = StrokeCap.Round)
            } else {
                // Escalando: CICLO de animación por altura (~cada 0,35m
                // alterna el brazo/pierna que sube) — el muñeco "trepa".
                val step = ((state.heightM / 0.35).toInt() % 2 == 0)
                if (step) {
                    // Brazo izq arriba estirado, derecho abajo; pierna der subiendo.
                    drawLine(bodyC, Offset(climberX, climberY - 6f), Offset(climberX - 19f, climberY - 22f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 2f), Offset(climberX - 13f, climberY + 12f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX - 17f, climberY + 24f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX + 6f, climberY + 35f), strokeWidth = 5f, cap = StrokeCap.Round)
                } else {
                    // Brazo der arriba, izq abajo; pierna izq subiendo.
                    drawLine(bodyC, Offset(climberX, climberY - 6f), Offset(climberX - 8f, climberY - 24f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 2f), Offset(climberX - 18f, climberY + 4f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX - 8f, climberY + 34f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(bodyC, Offset(climberX, climberY + 20f), Offset(climberX - 18f, climberY + 28f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }
        }

        // ---- HUD compacto arriba ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudChip("MANO", if (hand == "LEFT") "IZQ" else "DER")
            HudChip("ALTURA", "%.1f m".format(state.heightM))
            HudChip("MEJOR", "%.1f m".format(state.bestHeightM))
            HudChip("DESPLOME", "${state.wallOverhangDeg.toInt()}°")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.Close, contentDescription = "Salir",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        // ---- Barra de esfuerzo abajo, grande y con etiquetas ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.lg)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TU FUERZA: ${pct.toInt()}%", style = EyebrowTextStyle,
                    color = if (pct >= state.requiredPct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text("NECESITAS: ${state.requiredPct.toInt()}%", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(Spacing.xs))
            EffortBar(pct = pct, requiredPct = state.requiredPct)
        }

        // ---- Game over ----
        if (state.phase == GripClimbGameEngine.Phase.GAME_OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.xl)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("¡Rapelado!", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = Spacing.md)) {
                        Text("%.1f".format(state.bestHeightM),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 56.sp
                            ),
                            color = MaterialTheme.colorScheme.primary)
                        Text(" m", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm))
                    }
                    Text("Altura máxima · ${difficultyLabel(difficulty)} · mano ${if (hand == "LEFT") "izquierda" else "derecha"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.lg))
                    Button(onClick = { viewModel.retry() }, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(2.dp)) { Text("VOLVER A INTENTAR") }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(2.dp)) {
                        Text("CAMBIAR AJUSTES", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun HudChip(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun EffortBar(pct: Double, requiredPct: Double) {
    val trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
    val outlineColor = MaterialTheme.colorScheme.outline
    val fillColor = if (pct >= requiredPct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val markerColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier.fillMaxWidth().height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor)
            .border(1.dp, outlineColor, RoundedCornerShape(2.dp))
    ) {
        val fillFrac = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
        drawRect(color = fillColor, size = androidx.compose.ui.geometry.Size(size.width * fillFrac, size.height))
        // Marcador del % mínimo exigido ahora mismo por la pared.
        val reqFrac = (requiredPct / 100.0).coerceIn(0.0, 1.0).toFloat()
        val markerX = size.width * reqFrac
        drawLine(markerColor, Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth = 4f)
    }
}

private fun difficultyLabel(d: GripClimbGameEngine.Difficulty): String = when (d) {
    GripClimbGameEngine.Difficulty.FACIL -> "Fácil"
    GripClimbGameEngine.Difficulty.MEDIO -> "Medio"
    GripClimbGameEngine.Difficulty.DIFICIL -> "Difícil"
}
