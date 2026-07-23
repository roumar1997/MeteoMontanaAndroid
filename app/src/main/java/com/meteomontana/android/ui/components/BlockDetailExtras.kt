package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import kotlinx.coroutines.launch

// Piezas de la ficha de piedra (reparto del antiguo BlockDetailDialog.kt de
// 787 líneas): desplegable OPCIONES, estrellas de valoración y compartir vía.

/** Desplegable "OPCIONES": agrupa editar vías / cambiar sector / editar (admin)
 *  / eliminar para no apilar 4 botones. */
@Composable
internal fun BlockOptionsSection(
    block: Block,
    isProposal: Boolean,
    optionsOpen: Boolean,
    onToggleOptions: () -> Unit,
    onAddLines: (() -> Unit)?,
    availableSectors: List<Block>?,
    onOpenSectorPicker: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRequestDelete: (() -> Unit)?
) {
    val hasOptions = !isProposal && (
        (onAddLines != null && block.type == "BLOCK") ||
        (onOpenSectorPicker != null && block.type == "BLOCK" && !availableSectors.isNullOrEmpty()) ||
        onEdit != null || onRequestDelete != null)
    if (hasOptions) {
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable(onClick = onToggleOptions)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OPCIONES", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text(if (optionsOpen) "▴" else "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }
    }

    // Botón editor por cara (corregir/repintar/añadir + cambiar foto) —
    // solo para BLOCK y si el caller lo permite.
    if (optionsOpen && onAddLines != null && block.type == "BLOCK" && !isProposal) {
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Terra, RoundedCornerShape(2.dp))
                .clickable(onClick = onAddLines)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(if (block.lines.isEmpty()) stringResource(R.string.block_add_routes) else stringResource(R.string.block_edit_routes),
                style = EyebrowTextStyle, color = Terra)
        }
    }

    // Botón "+ ASIGNAR / CAMBIAR SECTOR" — BLOCK si la escuela tiene algún
    // sector. Si ya tiene → "CAMBIAR SECTOR" (el picker muestra los demás;
    // si no hay otro, lo avisa). El backend sobrescribe el sector al aprobar.
    if (optionsOpen && onOpenSectorPicker != null && block.type == "BLOCK" && !isProposal
            && !availableSectors.isNullOrEmpty()) {
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Terra, RoundedCornerShape(2.dp))
                .clickable(onClick = onOpenSectorPicker)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (block.sectorBlockId == null) stringResource(R.string.propose_assign_sector) else stringResource(R.string.propose_change_sector),
                style = EyebrowTextStyle, color = Terra
            )
        }
    }
    // (El antiguo botón "✎ CORREGIR VÍA" por vía se quitó: el editor por
    // cara de arriba ("EDITAR / CORREGIR VÍAS") ya corrige las existentes.)

    // Botón "EDITAR" — admin: mover posición, renombrar, editar líneas
    if (optionsOpen && onEdit != null && !isProposal) {
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
                .clickable(onClick = onEdit)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text("✎ EDITAR", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }

    // Botón "BORRAR" — solo si el caller lo permite (admins) y no es propuesta
    if (optionsOpen && onRequestDelete != null && !isProposal) {
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable(onClick = onRequestDelete)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text("🗑 BORRAR", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Fila de 5 estrellas tocables para valorar una vía.
 * Muestra la media de votos y permite al usuario dar/cambiar su valoración.
 */
@Composable
internal fun LineStarsRow(
    lineId: String,
    avgStars: Float?,
    myStars: Int,
    onRate: (Int) -> Unit
) {
    // Estilo Google Play: las estrellas muestran la MEDIA (amarillo), y son
    // tocables para votar. Tu toque se ve al instante (optimista) y luego el
    // dato refrescado recalcula la media.
    var pending by remember(lineId) { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(avgStars, myStars) { pending = null }
    val avgRounded = avgStars?.let { Math.round(it) } ?: 0
    val shown = pending ?: avgRounded
    val amber = Color(0xFFF59E0B)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 4.dp)
    ) {
        for (i in 1..5) {
            val filled = i <= shown
            Text(
                if (filled) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (filled) amber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val newStars = if (myStars == i) 0 else i   // re-tocar tu voto → quitarlo
                        pending = newStars.takeIf { it > 0 }
                        onRate(newStars)
                    }
            )
        }
        // Media numérica + marca discreta de tu voto.
        if (avgStars != null && avgStars > 0f) {
            Text(
                "%.1f".format(avgStars),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (myStars > 0) {
            Text(
                "· tu voto ${myStars}★",
                style = MaterialTheme.typography.labelSmall,
                color = amber,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Comparte una vía: intenta generar la IMAGEN (foto + líneas dibujadas, formato
 * historia) para que en el menú salga Instagram/WhatsApp con la foto ya adjunta;
 * si la vía no tiene foto o dibujo, cae al compartir de texto de siempre.
 * Descargar la foto es `suspend`, por eso se lanza en una corrutina.
 */
internal fun shareVia(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    block: Block,
    line: com.meteomontana.android.domain.model.BlockLine,
    schoolName: String,
    tickedIds: Set<String>,
    projectIds: Set<String>,
    sectorName: String?
) {
    scope.launch {
        val shared = runCatching {
            com.meteomontana.android.ui.share.shareLineAsImage(
                context, block, line, schoolName, tickedIds, projectIds, sectorName
            )
        }.getOrDefault(false)
        if (!shared) shareLine(context, block, line, schoolName, sectorName)
    }
}

/** Comparte una vía/bloque: texto según disciplina + enlace que abre la app. */
private fun shareLine(
    context: android.content.Context,
    block: Block,
    line: com.meteomontana.android.domain.model.BlockLine,
    schoolName: String,
    sectorName: String?
) {
    val kind = if (block.discipline.equals("ROUTE", ignoreCase = true)) "vía" else "bloque"
    val article = if (kind == "vía") "esta" else "este"
    val grade = line.grade?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val where = buildString {
        append(block.name)
        if (schoolName.isNotBlank()) append(" · ").append(schoolName)
        if (!sectorName.isNullOrBlank()) append(" · ").append(sectorName)
    }
    val base = com.meteomontana.android.BuildConfig.API_BASE_URL.removeSuffix("api/")
    val link = "${base}s/v/${block.schoolId}/${line.id}"
    val text = "🧗 Mira $article $kind: «${line.name}»$grade\n" +
        "📍 $where\n" +
        "👉 Míralo en Cumbre (foto con la línea dibujada):\n" +
        link
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(intent, "Compartir $kind"))
    }
}
