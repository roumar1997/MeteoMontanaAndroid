package com.meteomontana.android.ui.onboarding

import com.meteomontana.android.util.AppText
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

private const val PREFS = "onboarding"
// v2: tour ampliado (6 pasos). Subir la versión re-muestra el tour una vez a
// quien ya pasó la v1 (útil al relanzar con más features; aún no hay usuarios).
private const val KEY_DONE = "done_v2"

fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

fun markOnboardingDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DONE, true).apply()
}

/** Un paso del tour. `showScale` pinta la leyenda de colores del índice. */
private data class OnbStep(
    val emoji: String,
    @androidx.annotation.StringRes val eyebrowRes: Int,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val bodyRes: Int,
    val showScale: Boolean = false
)

private val STEPS = listOf(
    OnbStep("⛰", R.string.onb_1_eyebrow, R.string.onb_1_title, R.string.onb_1_body),
    OnbStep("🌡", R.string.onb_2_eyebrow, R.string.onb_2_title, R.string.onb_2_body,
        showScale = true),
    OnbStep("🗺", R.string.onb_3_eyebrow, R.string.onb_3_title, R.string.onb_3_body),
    OnbStep("📅", R.string.onb_4_eyebrow, R.string.onb_4_title, R.string.onb_4_body),
    OnbStep("⭐", R.string.onb_5_eyebrow, R.string.onb_5_title, R.string.onb_5_body),
    OnbStep("📓", R.string.onb_6_eyebrow, R.string.onb_6_title, R.string.onb_6_body),
    OnbStep("🧗", R.string.onb_7_eyebrow, R.string.onb_7_title, R.string.onb_7_body),
    OnbStep("💬", R.string.onb_8_eyebrow, R.string.onb_8_title, R.string.onb_8_body),
    OnbStep("📍", R.string.onb_9_eyebrow, R.string.onb_9_title, R.string.onb_9_body)
)

/**
 * Tour de primera apertura (6 pasos, estilo Cumbre, saltable). El permiso de
 * ubicación se pide DESPUÉS de explicar para qué sirve (mejor aceptación). Al
 * terminar o saltar se llama a `onFinish` (que pide ubicación y marca hecho).
 */
@Composable
fun OnboardingOverlay(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEPS.lastIndex
    val s = STEPS[step.coerceIn(0, last)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xl)
    ) {
        // Saltar (esquina superior derecha) — salvo en el último paso.
        if (step < last) {
            Text(stringResource(R.string.onboarding_overlay_v2_saltar),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onFinish() }
                    .padding(Spacing.sm))
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(s.emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(s.eyebrowRes), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(s.titleRes),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(s.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)

            if (s.showScale) {
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreDot(0xFF4A7C59); Text("  70+ ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScoreDot(0xFFC8843A); Text("  50–69 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScoreDot(0xFFB94040); Text("  <50",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // Indicador de pasos.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(STEPS.size) { i ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            if (i == step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { if (step < last) step += 1 else onFinish() }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(if (step < last) R.string.onb_next else R.string.onb_allow_location_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

@Composable
private fun ScoreDot(argb: Long) {
    Box(
        Modifier.size(10.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}
