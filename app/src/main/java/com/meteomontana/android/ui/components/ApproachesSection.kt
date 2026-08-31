package com.meteomontana.android.ui.components

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
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.Approach
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.Warn
import java.util.Locale

/**
 * Sección APROXIMACIONES de la ficha de escuela — Fase 1+ de
 * APPROACH_DESIGN.md §6.1. Espejo de ApproachesSection.swift (iOS).
 *
 * SOLO LECTURA para cualquier usuario; grabar/borrar es admin-only por ahora
 * (§2.6/§10 — pendiente revisión legal de términos antes de abrirlo a todos).
 */
@Composable
fun ApproachesSection(
    approaches: List<Approach>,
    isAdmin: Boolean,
    onFollow: (Approach) -> Unit,
    onRecord: () -> Unit,
    onDelete: (Approach) -> Unit
) {
    if (approaches.isEmpty() && !isAdmin) return

    var deleting by remember { mutableStateOf<Approach?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            "APROXIMACIONES",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )
        Spacer(Modifier.padding(top = Spacing.xs))

        approaches.forEach { a ->
            ApproachCard(
                approach = a,
                isAdmin = isAdmin,
                onFollow = { onFollow(a) },
                onDelete = { deleting = a },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }

        // Visible SOLO para admin por ahora — la pantalla que abre es la
        // definitiva, la que verá cualquier usuario cuando se active para
        // todos (ver APPROACH_DESIGN.md §2.6/§10).
        if (isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Terra, RoundedCornerShape(2.dp))
                    .clickable(onClick = onRecord)
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("+ GRABAR APROXIMACIÓN", style = EyebrowTextStyle, color = Terra)
            }
        }
    }

    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar «${a.name ?: "esta aproximación"}»?") },
            text = { Text("Se borra el camino y todas sus chinchetas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(a)
                    deleting = null
                }) { Text("BORRAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("CANCELAR") }
            }
        )
    }
}

@Composable
private fun ApproachCard(
    approach: Approach,
    isAdmin: Boolean,
    onFollow: () -> Unit,
    onDelete: () -> Unit,
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
                approach.name ?: "Aproximación",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isAdmin) {
                Box(
                    modifier = Modifier.padding(start = Spacing.sm)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                if (approach.isVerified) "✓ VERIFICADA" else "⚠ SIN VERIFICAR",
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
                Text("SEGUIR", style = EyebrowTextStyle,
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
    a.durationMin?.let { parts += "~$it min" }
    val pinCount = a.pins.size
    if (pinCount > 0) parts += if (pinCount == 1) "1 chincheta" else "$pinCount chinchetas"
    return parts.joinToString(" · ")
}
