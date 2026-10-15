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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.guide.GuideController
import com.meteomontana.android.ui.guide.GuidesToggleRow
import com.meteomontana.android.ui.guide.SharedGuidePrefs
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

private const val PREFS = "onboarding"
// v2: se mantiene la clave para no volver a enseñar la bienvenida a quien ya la pasó (ahora es de una sola pantalla).
private const val KEY_DONE = "done_v2"

fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

/**
 * Marca la bienvenida como vista y deja pendiente la guía de Escuelas (sale sola una vez, saltable, si las guías están
 * encendidas): es lo que el usuario ve justo después de crear la cuenta.
 */
fun markOnboardingDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DONE, true).apply()
    SharedGuidePrefs(context).setBool(GuideController.KEY_PENDING_SCHOOLS, true)
}

/**
 * Bienvenida de primera apertura: UNA sola pantalla (2026-10-14; antes un tour de 9 de texto que casi nadie leía).
 * Solo lo imprescindible: qué es Cumbre en una línea, qué significa el índice y para qué se usa la ubicación, explicado
 * ANTES de pedirla. Lo demás se aprende en la propia app (guía corta de primera vez y "? AYUDA" por pantalla), y aquí
 * mismo se decide si se quieren esas guías.
 *
 * [onFinish] = "CONTINUAR": el llamante pide SIEMPRE el permiso de ubicación del sistema y marca la bienvenida como vista.
 * Misma regla que iOS (Apple, guideline 5.1.1(iv), 2026-10-14): el botón es neutro ("Continuar", no "Permitir
 * ubicación…") y NO hay forma de saltarse el diálogo del sistema desde esta pantalla (antes había un "Ahora no").
 * Quien no quiera dar la ubicación lo dice en el diálogo de Android, y la puede dar luego desde Escuelas.
 */
@Composable
fun OnboardingOverlay(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Entre las barras del sistema: el botón no puede quedar bajo la barra de navegación.
            .systemBarsPadding()
            .padding(Spacing.xl)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⛰", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.welcome_eyebrow), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text("Cumbre",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.welcome_lead),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.md))
            // El índice 0–100, en una línea de leyenda por tramo.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LegendRow(0xFF4A7C59, stringResource(R.string.welcome_scale_good))
                LegendRow(0xFFC8843A, stringResource(R.string.welcome_scale_fair))
                LegendRow(0xFFB94040, stringResource(R.string.welcome_scale_bad))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(R.string.welcome_location_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(Spacing.md))
        GuidesToggleRow(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(14.dp)
        )
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(terraFillColor())
                .clickable(onClick = onFinish)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.onb_continue),
                style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun LegendRow(argb: Long, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
