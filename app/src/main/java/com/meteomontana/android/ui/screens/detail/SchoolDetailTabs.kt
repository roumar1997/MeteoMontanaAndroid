package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.terraFillColor

/** Pestañas de la ficha de escuela, bajo la tarjeta HOY. El mapa va por defecto (como en iOS). */
enum class SchoolTab(val guideKey: String) {
    MAP("detail_map"), BLOCKS("detail_blocks"), WEATHER("detail_weather"), NOTES("detail_notes")
}

/** Qué pone la pestaña de las vías: solo bloques, solo vías, o las dos cosas. */
enum class BlocksTabKind { BOULDERS, ROUTES, BOTH }

/**
 * "BLOQUES" si solo hay bloques, "VÍAS" si solo hay vías y "BLOQUES/VÍAS" si hay de las dos (`school.style` =
 * "Vía,Bloque"). Sin estilo, "BLOQUES". Pura, para poder probarla: la pantalla solo elige el texto.
 */
fun blocksTabKind(style: String?): BlocksTabKind {
    val styles = style?.split(",")?.map { it.trim().uppercase() }?.toSet().orEmpty()
    val hasRoutes = "VÍA" in styles || "VIA" in styles
    val hasBoulders = "BLOQUE" in styles
    return when {
        hasRoutes && hasBoulders -> BlocksTabKind.BOTH
        hasRoutes -> BlocksTabKind.ROUTES
        else -> BlocksTabKind.BOULDERS
    }
}

/** Las cuatro pestañas en píldoras, a ancho completo: la activa va rellena de terracota. */
@Composable
fun SchoolTabBar(
    selected: SchoolTab,
    onSelect: (SchoolTab) -> Unit,
    blocksKind: BlocksTabKind,
    modifier: Modifier = Modifier
) {
    val labels = mapOf(
        SchoolTab.MAP to stringResource(R.string.detail_tab_map),
        SchoolTab.BLOCKS to stringResource(
            when (blocksKind) {
                BlocksTabKind.BOULDERS -> R.string.detail_tab_boulders
                BlocksTabKind.ROUTES -> R.string.detail_tab_routes
                BlocksTabKind.BOTH -> R.string.detail_tab_boulders_routes
            }
        ),
        SchoolTab.WEATHER to stringResource(R.string.detail_tab_weather),
        SchoolTab.NOTES to stringResource(R.string.detail_tab_notes)
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SchoolTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) terraFillColor() else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (active) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labels.getValue(tab),
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp,
                    maxLines = 1, textAlign = TextAlign.Center
                )
            }
        }
    }
}
