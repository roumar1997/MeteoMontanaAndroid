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
    onCorrectSchoolName: () -> Unit,
    onCorrectSchoolStyle: () -> Unit,
    onDismiss: () -> Unit
) {
    CumbreDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.propose_picker_dialogs_v2_proponer_mejora),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.propose_picker_dialogs_v2_falta_algo_en_esta_escuela),
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
                stringResource(R.string.propose_picker_dialogs_v2_como_funciona_elige_que_anadir),
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
                label = stringResource(R.string.propose_picker_dialogs_v2_continuar_piedra_a_medias),
                description = stringResource(R.string.propose_picker_dialogs_v3_tienes_una_piedra_sin_terminar),
                enabled = true,
                onClick = onContinuarBorrador
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        TypeOption(
            icon = "▲",
            label = stringResource(R.string.propose_picker_dialogs_v2_piedra),
            description = stringResource(R.string.propose_picker_dialogs_v3_una_roca_con_sus_vias),
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
                label = stringResource(R.string.propose_picker_dialogs_v2_piedra_desde_una_foto),
                description = stringResource(R.string.propose_picker_dialogs_v3_elige_una_foto_tuya_de),
                enabled = true,
                onClick = onBoulderFromPhoto
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        TypeOption(
            icon = "+",
            label = stringResource(R.string.propose_picker_dialogs_v2_sector),
            description = stringResource(R.string.propose_picker_dialogs_v3_una_zona_que_agrupa_varias),
            enabled = true,
            onClick = onSector
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "▬",
            label = stringResource(R.string.propose_picker_dialogs_v2_parking),
            description = stringResource(R.string.propose_picker_dialogs_v3_el_punto_donde_se_aparca),
            enabled = true,
            onClick = onParking
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "↔",
            label = stringResource(R.string.propose_picker_dialogs_v2_corregir),
            description = stringResource(R.string.propose_picker_dialogs_v3_algo_esta_mal_colocado_en),
            enabled = true,
            onClick = onCorrection
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "T",
            label = stringResource(R.string.propose_picker_dialogs_v2_corregir_nombre),
            description = stringResource(R.string.propose_picker_dialogs_v3_el_nombre_de_la_escuela),
            enabled = true,
            onClick = onCorrectSchoolName
        )
        Spacer(Modifier.height(Spacing.sm))
        TypeOption(
            icon = "☑",
            label = stringResource(R.string.propose_picker_dialogs_v2_corregir_estilo),
            description = stringResource(R.string.propose_picker_dialogs_v3_esta_escuela_tiene_via_y),
            enabled = true,
            onClick = onCorrectSchoolStyle
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
                Text(stringResource(R.string.propose_picker_dialogs_guardada_en_tu_movil), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.propose_picker_dialogs_v2_se_enviara_automaticamente_en_cuanto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isAdmin) {
                Text(stringResource(R.string.propose_success_admin), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.propose_picker_dialogs_v2_se_ha_publicado_directamente_en),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.propose_success), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.propose_picker_dialogs_un_admin_la_revisara),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.", style = MaterialTheme.typography.bodyMedium, color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.propose_picker_dialogs_v2_te_avisaremos_por_email_y),
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
