package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * Icono de "cómo llegar"/compartir para la procesionaria del pino: con un
 * círculo rojo detrás (como una insignia de aviso) cuando toca avisar de
 * verdad, neutro el resto del tiempo — pero SIEMPRE visible, para que se
 * pueda reportar en cualquier escuela (Álvaro, 2026-09-05: no hay datos de
 * dónde hay pinos, solo lo que la gente confirma).
 */
@Composable
fun ProcessionaryButton(alertActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        if (alertActive) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Text("🐛", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("🐛", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/**
 * Botón-toggle: relleno cuando está pulsado, solo borde cuando no — para que
 * se note a simple vista que algo quedó marcado, sin depender de leer texto
 * (Álvaro, 2026-09-05: "que se note que lo has marcado").
 */
@Composable
private fun ToggleButton(
    label: String,
    pressed: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) accent else Color.Transparent)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pressed) {
                Text("✓ ", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold), color = Color.White)
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                color = if (pressed) Color.White else accent
            )
        }
    }
}

@Composable
fun ProcessionaryInfoSheet(
    hasKnownProcessionary: Boolean,
    alertActive: Boolean,
    activeNowSet: Boolean,
    onConfirm: () -> Unit,
    onRetract: () -> Unit,
    onActiveNow: () -> Unit,
    onClearActiveNow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(Spacing.lg)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "🐛 Procesionaria del pino",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.padding(top = Spacing.sm))

                // Botones arriba del todo: son lo primero que hay que poder
                // pulsar, sin tener que hacer scroll para llegar a ellos
                // (Álvaro, 2026-09-05: "que esté arriba... no nada abajo").
                ToggleButton(
                    label = "Sí que hay en este sector",
                    pressed = hasKnownProcessionary,
                    accent = Terra,
                    onClick = { if (hasKnownProcessionary) onRetract() else onConfirm() }
                )
                Spacer(Modifier.padding(top = Spacing.sm))
                ToggleButton(
                    label = "Las he visto antes de tiempo",
                    pressed = activeNowSet,
                    accent = MaterialTheme.colorScheme.error,
                    onClick = { if (activeNowSet) onClearActiveNow() else onActiveNow() }
                )
                Spacer(Modifier.padding(top = Spacing.xs))
                Text(
                    "Ambos se pueden marcar y desmarcar — si te equivocas al pulsar, vuelve a pulsar para quitarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(top = Spacing.md))

                if (alertActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            "⚠ En esta escuela ya se han visto, y estamos en su época orientativa (más o menos) — extrema la precaución, sobre todo si vas con perro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.padding(top = Spacing.md))
                } else if (hasKnownProcessionary) {
                    Text(
                        "Aquí se han visto otros años, pero ahora mismo estamos fuera de su época orientativa (diciembre-mayo aprox.).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = Spacing.md))
                }

                Text(
                    "Son las orugas del pino, activas sobre todo en invierno y primavera. Sus pelillos son urticantes: para personas dan picor y alergia, pero para los perros pueden ser muy graves — si un perro las toca o las lame se le puede hinchar e incluso necrosar la lengua, y a veces hace falta amputarla para salvarlo. Mantén a tu perro alejado de los procesionarios (bolsas blancas en las ramas) y de las orugas en el suelo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(top = Spacing.sm))
                Text(
                    "No hay ningún mapa fiable de dónde hay pinos con procesionaria — la única forma de saberlo es que alguien las haya visto. Si las ves aquí, marca \"Sí que hay en este sector\": la escuela quedará avisando cada temporada, sin que nadie tenga que repetirlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasKnownProcessionary) {
                    Spacer(Modifier.padding(top = Spacing.md))
                    Text(
                        "Confirmado — gracias por avisar.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!alertActive) {
                    Spacer(Modifier.padding(top = Spacing.sm))
                    Text(
                        "\"Las he visto antes de tiempo\" activa el aviso aunque no sea su época típica — se apaga sola en unas semanas si nadie más la confirma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
