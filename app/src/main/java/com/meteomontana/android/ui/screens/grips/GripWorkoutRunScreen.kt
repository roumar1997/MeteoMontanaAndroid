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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.grips.GripWorkoutEngine
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@Composable
fun GripWorkoutRunScreen(
    workoutId: String,
    onBack: () -> Unit,
    viewModel: GripWorkoutRunViewModel = hiltViewModel()
) {
    val workout by viewModel.workout.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val points by viewModel.points.collectAsState()
    val currentKg by viewModel.currentKg.collectAsState()
    val sessionMaxKg by viewModel.sessionMaxKg.collectAsState()

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; viewModel.stop() }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(workout?.name ?: "Entreno", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (viewModel.connectedDeviceId == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Conecta tu báscula antes de entrenar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val es = engineState
        if (workout == null || es == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            return@Column
        }

        if (es.finished) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡Entreno completado!", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            return@Column
        }

        val currentSet = workout!!.sets.getOrNull(es.setIndex)
        Text(
            "SET ${es.setIndex + 1} DE ${workout!!.sets.size}" +
                (currentSet?.let { " · ${it.reps} REPS" } ?: ""),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md), textAlign = TextAlign.Center)
        viewModel.currentGripLabel()?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(Spacing.sm))
        HandIndicator(es)
        Spacer(Modifier.height(Spacing.sm))

        val range = viewModel.currentTargetRangeKg()
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(currentKg),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = com.meteomontana.android.ui.theme.Mono,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = when {
                        range == null -> MaterialTheme.colorScheme.onBackground
                        currentKg in range.first..range.second -> MaterialTheme.colorScheme.primary
                        es.activeHand == GripWorkoutEngine.Hand.NONE -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.error
                    })
                Text(" kg", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            if (range != null) {
                Text("OBJETIVO: %.1f – %.1f kg".format(range.first, range.second),
                    style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (es.activeHand != GripWorkoutEngine.Hand.NONE) {
                Text("Sin máximo guardado para este agarre y mano — mide tu máximo para tener objetivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.lg))
            }
        }

        GripLineChart(
            points = points, modifier = Modifier.padding(Spacing.lg),
            targetMin = range?.first?.toFloat(), targetMax = range?.second?.toFloat(),
            yMaxKg = sessionMaxKg
        )

        val totalReps = currentSet?.reps ?: 0
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HandPanel("IZQUIERDA", es.left, active = es.activeHand == GripWorkoutEngine.Hand.LEFT,
                totalReps = totalReps, handMode = workout!!.handMode, modifier = Modifier.weight(1f))
            HandPanel("DERECHA", es.right, active = es.activeHand == GripWorkoutEngine.Hand.RIGHT,
                totalReps = totalReps, handMode = workout!!.handMode, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HandIndicator(es: GripWorkoutEngine.EngineState) {
    val label = when (es.activeHand) {
        GripWorkoutEngine.Hand.LEFT -> "TIRA CON LA IZQUIERDA"
        GripWorkoutEngine.Hand.RIGHT -> "TIRA CON LA DERECHA"
        GripWorkoutEngine.Hand.NONE -> "DESCANSO"
    }
    val color = when (es.activeHand) {
        GripWorkoutEngine.Hand.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(color)
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun HandPanel(
    label: String,
    hs: GripWorkoutEngine.HandState,
    active: Boolean,
    totalReps: Int,
    handMode: String,
    modifier: Modifier = Modifier
) {
    val seconds = (hs.remainingMs / 1000).coerceAtLeast(0)
    val phaseLabel = when (hs.phase) {
        GripWorkoutEngine.Phase.WORK -> "TRABAJO"
        GripWorkoutEngine.Phase.REST, GripWorkoutEngine.Phase.REST_JUST_ENDED_WORK -> "DESCANSO"
        GripWorkoutEngine.Phase.WAITING -> "ESPERANDO"
        GripWorkoutEngine.Phase.DONE -> "HECHO"
    }
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .border(
                if (active) 2.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
            )
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = EyebrowTextStyle,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${seconds}s",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = com.meteomontana.android.ui.theme.Mono,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground)
        Text(phaseLabel, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Progreso de reps de ESTA mano en el set. En POR_REP las reps se
        // reparten entre las dos manos (contador total conjunto).
        if (totalReps > 0) {
            val repsThisHand = if (handMode == "UNA") totalReps else (totalReps + 1) / 2
            Text("REP ${hs.repIndex.coerceAtMost(repsThisHand)}/${repsThisHand}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
