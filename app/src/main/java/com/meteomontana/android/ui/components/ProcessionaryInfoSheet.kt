package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

/**
 * Silueta de oruga peluda en diagonal (cabeza oscura abajo, cola arriba, con
 * pelillos de punta en abanico) — dibujo elegido por Álvaro de una lámina de
 * referencia de 16 poses (2026-09-05, "usa la 1"). Sin emoji, para que
 * combine con el resto de iconografía Cumbre.
 */
@Composable
fun ProcessionaryIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 64f
        // Segmentos del cuerpo (de la cabeza hacia la cola), en un espacio de 64x64.
        val segs = listOf(
            Offset(15.7f, 39.2f) to 7.4f,
            Offset(21.5f, 33.2f) to 6.9f,
            Offset(27.9f, 27.7f) to 6.4f,
            Offset(34.1f, 23.5f) to 5.9f,
            Offset(40.4f, 20.2f) to 5.4f,
            Offset(47.4f, 17.6f) to 4.9f,
            Offset(54.0f, 16.0f) to 4.4f
        )
        val headCenter = Offset(10f, 46f)
        val headRadius = 8.4f

        // Cuerpo: cápsulas superpuestas, más gruesas cerca de la cabeza.
        segs.forEach { (pt, r) ->
            drawCircle(color = tint, radius = r * s, center = Offset(pt.x * s, pt.y * s))
        }

        // Pelillos de punta: 3 por segmento, en abanico hacia arriba (el rasgo
        // urticante que hace peligrosa a la procesionaria de verdad).
        segs.forEach { (pt, r) ->
            val cx = pt.x * s
            val cy = (pt.y - r * 0.6f) * s
            val len = r * 1.7f * s
            listOf(-35.0, -8.0, 20.0).forEach { degFromUp ->
                val angle = Math.toRadians(degFromUp - 90.0)
                val dx = (len * kotlin.math.cos(angle)).toFloat()
                val dy = (len * kotlin.math.sin(angle)).toFloat()
                drawLine(tint, Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 1.2f * s, cap = StrokeCap.Round)
            }
        }

        // Cabeza oscura + un par de patitas.
        val hc = Offset(headCenter.x * s, headCenter.y * s)
        drawCircle(color = tint, radius = headRadius * s, center = hc)
        drawLine(tint, hc + Offset(-1f * s, 7f * s), hc + Offset(-4f * s, 12f * s), strokeWidth = 1.3f * s, cap = StrokeCap.Round)
        drawLine(tint, hc + Offset(3f * s, 7f * s), hc + Offset(2f * s, 13f * s), strokeWidth = 1.3f * s, cap = StrokeCap.Round)
    }
}

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
                ProcessionaryIcon(tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            ProcessionaryIcon(tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProcessionaryIcon(tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.padding(start = Spacing.xs))
                    Text(
                        "Procesionaria del pino",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.padding(top = Spacing.xs))
                Text(
                    "Época habitual: de diciembre a mayo (orientativo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    "\"Sí que hay en este sector\" marca la escuela para siempre: cada diciembre-mayo avisará sola, sin que nadie tenga que repetirlo. \"Las he visto antes de tiempo\" enciende el aviso YA, aunque estemos fuera de esos meses. Ambos se pueden marcar y desmarcar — si te equivocas al pulsar, vuelve a pulsar para quitarlo.",
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
