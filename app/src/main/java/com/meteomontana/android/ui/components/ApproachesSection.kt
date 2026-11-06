package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.terraFillColor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.Warn
import java.util.Locale

/**
 * Sección APROXIMACIONES de la ficha de escuela — Fase 1+ de
 * docs/APPROACH_DESIGN.md §6.1. Espejo de ApproachesSection.swift (iOS).
 *
 * SOLO LECTURA: grabar y borrar caminos vive en el panel de admin de iOS.
 */
@Composable
fun ApproachesSection(
    approaches: List<Approach>,
    onFollow: (Approach) -> Unit
) {
    if (approaches.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            stringResource(R.string.approaches_title),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )
        Spacer(Modifier.padding(top = Spacing.xs))

        approaches.forEach { a ->
            ApproachCard(
                approach = a,
                onFollow = { onFollow(a) },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
    }
}

@Composable
private fun ApproachCard(
    approach: Approach,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text(
                approach.name ?: stringResource(R.string.approaches_default_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            summaryLine(approach),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(top = Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (approach.isVerified) "✓ VERIFICADA" else stringResource(R.string.approaches_section_v3_sin_verificar),
                style = EyebrowTextStyle,
                color = if (approach.isVerified) Ok else Warn,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(terraFillColor())
                    .clickable(onClick = onFollow)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Text(stringResource(R.string.approaches_section_v2_seguir), style = EyebrowTextStyle,
                    color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

private fun summaryLine(a: Approach): String {
    val parts = mutableListOf<String>()
    a.distanceM?.let { d ->
        parts += if (d >= 1000) String.format(Locale.US, "%.1f km", d / 1000.0) else "$d m"
    }
    a.ascentM?.let { parts += "+$it m" }
    a.durationMin?.let { parts += AppText.get(R.string.approaches_section_v4_min, it) }
    val pinCount = a.pins.size
    if (pinCount > 0) parts += if (pinCount == 1) AppText.get(R.string.approaches_section_v4_1_chincheta) else "$pinCount chinchetas"
    return parts.joinToString(" · ")
}
