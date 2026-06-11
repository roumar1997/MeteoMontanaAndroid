package com.meteomontana.android.ui.onboarding

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
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

private const val PREFS = "onboarding"
private const val KEY_DONE = "done_v1"

fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

fun markOnboardingDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DONE, true).apply()
}

/**
 * Overlay de primera apertura: 2 pasos estilo Cumbre. El permiso de ubicación
 * se pide DESPUÉS de explicar para qué sirve (mejor tasa de aceptación y menos
 * desconfianza que el dialog a pelo).
 */
@Composable
fun OnboardingOverlay(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                0 -> {
                    Text("⛰", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(Spacing.lg))
                    Text("EL ÍNDICE 0–100", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Cumbre resume en un número las condiciones de escalada " +
                        "de cada escuela: temperatura, humedad, viento, lluvia " +
                        "reciente y cuánto tarda en secar su roca.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreDot(0xFF4A7C59) ; Text("  70+ a escalar   ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ScoreDot(0xFFC8843A) ; Text("  50–69 regular   ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ScoreDot(0xFFB94040) ; Text("  <50 mal día",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                1 -> {
                    Text("📍", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(Spacing.lg))
                    Text("TU UBICACIÓN", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Te pediremos permiso de ubicación para ordenar las " +
                        "escuelas por cercanía y centrar el mapa donde estás. " +
                        "Solo se usa en tu móvil; nunca se comparte.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // Indicador de paso (2 puntos)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(2) { i ->
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
                    .clickable {
                        if (step == 0) step = 1 else onFinish()
                    }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (step == 0) "SIGUIENTE" else "PERMITIR UBICACIÓN Y EMPEZAR",
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
