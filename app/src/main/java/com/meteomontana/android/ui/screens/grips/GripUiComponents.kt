package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.GripMaxRecord
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing

/**
 * Selector de agarre en DOS ejes (DEDOS × ESTILO) — mucho más claro que una
 * sola fila con las 15 combinaciones. Dado el catálogo [gripTypes], al tocar
 * un chip se busca el GripType con la combinación resultante.
 */
@Composable
fun GripTypeTwoAxisSelector(
    gripTypes: List<GripType>,
    selected: GripType?,
    enabled: Boolean = true,
    onSelect: (GripType) -> Unit
) {
    val currentFinger = selected?.fingerGroup ?: FINGER_GROUP_ORDER.first()
    val currentStyle = selected?.style ?: GRIP_STYLE_ORDER.first()

    fun pick(finger: String, style: String) {
        gripTypes.firstOrNull { it.fingerGroup == finger && it.style == style }?.let(onSelect)
    }

    Column {
        Text("DEDOS", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FINGER_GROUP_ORDER.filter { f -> gripTypes.any { it.fingerGroup == f } }.forEach { f ->
                GripChip(
                    label = fingerGroupLabel(f),
                    selected = f == currentFinger,
                    enabled = enabled,
                    onClick = { pick(f, currentStyle) }
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text("ESTILO", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            GRIP_STYLE_ORDER.filter { s -> gripTypes.any { it.style == s } }.forEach { s ->
                GripChip(
                    label = gripStyleLabel(s),
                    selected = s == currentStyle,
                    enabled = enabled,
                    onClick = { pick(currentFinger, s) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun GripChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Selector IZQUIERDA / DERECHA grande y claro. */
@Composable
fun HandSelector(
    hand: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf("LEFT" to "IZQUIERDA", "RIGHT" to "DERECHA").forEach { (value, label) ->
            val isSel = hand == value
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Tabla de máximos: una fila por agarre medido, columnas IZQ / DER bien
 * diferenciadas. La mano más fuerte de cada agarre va en terracota.
 */
@Composable
fun GripMaxesTable(
    gripTypes: List<GripType>,
    maxes: List<GripMaxRecord>,
    modifier: Modifier = Modifier
) {
    val byGrip = maxes.groupBy { it.gripTypeId }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
    ) {
        // Cabecera de columnas.
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Text("AGARRE", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("IZQ", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            Text("DER", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Filas en orden canónico de agarre.
        val ordered = gripTypes.sortedWith(
            compareBy({ FINGER_GROUP_ORDER.indexOf(it.fingerGroup) }, { GRIP_STYLE_ORDER.indexOf(it.style) })
        ).filter { byGrip.containsKey(it.id) }

        ordered.forEachIndexed { idx, gripType ->
            val records = byGrip[gripType.id].orEmpty()
            val left = records.firstOrNull { it.hand == "LEFT" }?.maxKg
            val right = records.firstOrNull { it.hand == "RIGHT" }?.maxKg
            Row(
                Modifier.fillMaxWidth()
                    .background(if (idx % 2 == 1) MaterialTheme.colorScheme.background else Color.Transparent)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fingerGroupLabel(gripType.fingerGroup), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(gripStyleLabel(gripType.style), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MaxKgCell(left, strongest = left != null && (right == null || left >= right))
                MaxKgCell(right, strongest = right != null && (left == null || right >= left))
            }
            if (idx < ordered.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MaxKgCell(kg: Double?, strongest: Boolean) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
        Text(
            kg?.let { "%.1f".format(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp
            ),
            color = when {
                kg == null -> MaterialTheme.colorScheme.onSurfaceVariant
                strongest -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onBackground
            }
        )
        if (kg != null) {
            Text("kg", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
