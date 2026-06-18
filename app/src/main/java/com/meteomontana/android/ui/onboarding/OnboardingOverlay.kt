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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing

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
    val eyebrow: String,
    val title: String,
    val body: String,
    val showScale: Boolean = false
)

private val STEPS = listOf(
    OnbStep("⛰", "BIENVENIDO", "Cumbre",
        "Tiempo para escalar. Te decimos cuándo y dónde se puede escalar, " +
        "con la roca seca, en 191 escuelas."),
    OnbStep("🌡", "EL ÍNDICE 0–100", "¿Hoy se puede?",
        "Un número resume las condiciones de cada escuela: temperatura, humedad, " +
        "viento, lluvia reciente y cuánto tarda en secar SU tipo de roca " +
        "(la arenisca tarda días; el granito, horas).",
        showScale = true),
    OnbStep("🗺", "MAPA Y TOPOS", "Cada piedra, al detalle",
        "Abre una escuela y verás parkings, sectores y piedras en el mapa. " +
        "Toca una piedra y aparece su foto con las VÍAS dibujadas, su grado y " +
        "cómo llegar."),
    OnbStep("📅", "PLANIFICA EL FINDE", "La mejor ventana",
        "Ventana óptima del día, mejor día de la semana, comparador de escuelas " +
        "y selector de días para decidir adónde ir."),
    OnbStep("⭐", "FAVORITAS Y ALERTAS", "No te pierdas el buen día",
        "Marca tus escuelas favoritas, míralas de un vistazo en el widget de inicio " +
        "y activa la alerta para que te avise cuando vaya a haber buena ventana."),
    OnbStep("📓", "TU DIARIO", "Lleva la cuenta",
        "Marca las vías que encadenas: la app guarda tu diario con tus estadísticas " +
        "y tu grado máximo, escuela por escuela."),
    OnbStep("🧗", "SUMA A LA GUÍA", "Comunidad",
        "Propón escuelas, piedras y sectores nuevos y deja notas con foto. Un admin " +
        "las revisa antes de publicarlas para toda la comunidad."),
    OnbStep("💬", "PERFIL, GENTE Y CHAT", "Conecta",
        "Crea tu perfil, busca y sigue a otros escaladores, mira sus diarios y " +
        "chatea 1 a 1. Las notificaciones te avisan de seguidores y mensajes."),
    OnbStep("📍", "OFFLINE + UBICACIÓN", "Listo para el monte",
        "Guarda escuelas para verlas SIN cobertura. Te pediremos la ubicación solo " +
        "para ordenar por cercanía y centrar el mapa: se usa en tu móvil, nunca se " +
        "comparte.")
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
            Text("Saltar",
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
            Text(s.eyebrow, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text(s.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.md))
            Text(s.body,
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
                    if (step < last) "SIGUIENTE" else "PERMITIR UBICACIÓN Y EMPEZAR",
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
