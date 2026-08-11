package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

// Diálogos de arranque y cierre del flujo de proponer (reparto del antiguo
// ProposeContributionFlow.kt): elegir tipo de mejora + éxito.

// ─── TypePickerDialog ────────────────────────────────────────────────────────

@Composable
internal fun TypePickerDialog(
    onParking: () -> Unit,
    onBoulder: () -> Unit,
    /** Null si el atajo de la foto no aplica en este contexto. */
    onBoulderFromPhoto: (() -> Unit)? = null,
    /** Null si no hay ninguna piedra a medias guardada en este movil. */
    onContinuarBorrador: (() -> Unit)? = null,
    onSector: () -> Unit,
    onCorrection: () -> Unit,
    onDismiss: () -> Unit
) {
    CumbreDialog(onDismiss = onDismiss) {
        Text(
            "Proponer mejora",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "¿Falta algo en esta escuela? Propón una mejora y un admin la revisará (24-48 h).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        // Mini-guía del flujo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
                .padding(Spacing.md)
        ) {
            Text(
                "Cómo funciona: elige qué añadir → toca el mapa para fijar la posición → rellena los datos → enviar. ¡Así de fácil!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        // Lo primero, porque es trabajo YA empezado: si esta ahi, es lo que el
        // usuario venia a terminar.
        if (onContinuarBorrador != null) {
            TypeOption(
                icon = "↺",
                label = "CONTINUAR PIEDRA A MEDIAS",
                description = "Tienes una piedra sin terminar en esta escuela. Sigue donde lo dejaste.",
                enabled = true,
                onClick = onContinuarBorrador
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        TypeOption(
            icon = "▲",
            label = "PIEDRA",
            description = "Una roca con sus vías de escalada. Podrás añadir fotos y dibujar las líneas de cada vía.",
            enabled = true,
            onClick = onBoulder
        )
        Spacer(Modifier.height(Spacing.sm))
        // Atajo: si la foto sabe donde se hizo, la piedra se coloca sola y no
        // hay que tocar el mapa. Aqui la escuela ya se conoce, asi que —a
        // diferencia del mismo atajo desde la lista de escuelas— no hay que
        // buscarla por cercania. Pensado para encadenar varias piedras sin
        // salir de la escuela.
        if (onBoulderFromPhoto != null) {
            TypeOption(
                icon = "\u25A3",
                label = "PIEDRA DESDE UNA FOTO",
                description = "Elige una foto tuya de la piedra: se coloca sola en el punto donde la hiciste.",
                enabled = true,
                onClick = onBoulderFromPhoto
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        TypeOption(
            icon = "+",
            label = "SECTOR",
            description = "Una zona que agrupa varias piedras (ej: \"La Isla\", \"Vertedero\"). Luego podrás asignar piedras al sector.",
            enabled = true,
            onClick = onSector
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "▬",
            label = "PARKING",
            description = "El punto donde se aparca para llegar a la escuela. Otros escaladores verán \"Cómo llegar\" con indicaciones.",
            enabled = true,
            onClick = onParking
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "↔",
            label = "CORREGIR",
            description = "¿Algo está mal colocado en el mapa? Toca el elemento y muévelo al sitio correcto.",
            enabled = true,
            onClick = onCorrection
        )

        Spacer(Modifier.height(Spacing.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onDismiss)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.common_close).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TypeOption(
    icon: String,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    // TARJETA, no fila con separador: es lo que hace iOS, y además una tarjeta
    // se lee como "esto se toca" mientras que una fila entre líneas parece
    // texto informativo. Aquí cada opción ES la decisión de la pantalla.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(icon, color = Terra.copy(alpha = alpha),
            style = MaterialTheme.typography.headlineMedium)
        Column {
            Text(label, style = EyebrowTextStyle, color = Terra.copy(alpha = alpha))
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

// ─── SuccessDialog ────────────────────────────────────────────────────────────

@Composable
internal fun SuccessDialog(
    isAdmin: Boolean = false,
    queued: Boolean = false,
    onClose: () -> Unit,
    onMyProposals: () -> Unit
) {
    CumbreDialog(onDismiss = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Moss),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Spacer(Modifier.height(Spacing.lg))
            if (queued) {
                Text("GUARDADA EN TU MÓVIL", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Se enviará automáticamente en cuanto\nhaya cobertura. No tienes que hacer nada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isAdmin) {
                Text(stringResource(R.string.propose_success_admin), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Se ha publicado directamente en el mapa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.propose_success), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Un admin la revisará en ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.", style = MaterialTheme.typography.bodyMedium, color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Te avisaremos por email y notificación\npush cuando haya respuesta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onClose).padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_close), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .clickable(onClick = onMyProposals).padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.propose_view_my), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}
