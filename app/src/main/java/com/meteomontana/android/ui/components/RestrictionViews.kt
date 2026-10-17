package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Restriction
import com.meteomontana.android.domain.model.RestrictionKind
import com.meteomontana.android.domain.model.SchoolRestrictions
import com.meteomontana.android.ui.theme.Bad
import com.meteomontana.android.ui.theme.BadDark
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Warn
import com.meteomontana.android.ui.theme.WarnDark

/**
 * Restricciones en vigor de la escuela abierta. Se da UNA vez en la ficha y la leen el chip del sector, la pestaña
 * BLOQUES y la ficha de la piedra, sin pasarla de mano en mano. Vacía fuera de una ficha.
 */
val LocalSchoolRestrictions = compositionLocalOf<SchoolRestrictionsUi?> { null }

/** Las restricciones de la escuela + sus piedras y sectores (para saber a qué sector pertenece cada piedra). */
class SchoolRestrictionsUi(val restrictions: SchoolRestrictions, val blocks: List<Block>) {
    fun forBlockId(blockId: String): List<Restriction> =
        restrictions.forBlock(blockId, blocks.firstOrNull { it.id == blockId }?.sectorBlockId)

    fun inSector(sectorId: String): Restriction? = restrictions.strongestInSector(sectorId, blocks)

    fun inSectorNamed(name: String?): Restriction? = restrictions.strongestInSectorNamed(name, blocks)

    /** Todas las de ese sector (el entero y sus piedras sueltas), para la hoja. */
    fun allInSector(sectorId: String): List<Restriction> {
        val inSector = blocks.filter { it.sectorBlockId == sectorId }.map { it.id }.toSet()
        return restrictions.active.filter { r -> r.sectorBlockId == sectorId || r.blockIds.any { it in inSector } }
    }
}

/** El "!" de un sector o piedra, tocable: abre la hoja con el detalle. Nada si no hay restricción. */
@Composable
fun RestrictionMarkButton(items: List<Restriction>, size: Int = 16) {
    if (items.isEmpty()) return
    val ui = LocalSchoolRestrictions.current
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    RestrictionMark(items.minBy { it.kind.ordinal }.kind, Modifier.clickable { open = true }, size)
    if (open) RestrictionsSheet(items, ui?.blocks.orEmpty()) { open = false }
}

/** Color de cada tipo: cerrado = rojo, regulado = ámbar, aviso = el gris del texto secundario. */
@Composable
fun restrictionColor(kind: RestrictionKind): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (kind) {
        RestrictionKind.CLOSED -> if (dark) BadDark else Bad
        RestrictionKind.REGULATED -> if (dark) WarnDark else Warn
        RestrictionKind.NOTICE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** El "!" redondo que va junto al sector o la piedra afectada. */
@Composable
fun RestrictionMark(kind: RestrictionKind, modifier: Modifier = Modifier, size: Int = 16) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(restrictionColor(kind)),
        contentAlignment = Alignment.Center
    ) {
        Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.7f).sp)
    }
}

/** Pastilla junto al nombre de la escuela: "! 2 CERRADOS" (o "! AVISO"). Nada si no hay restricciones en vigor. */
@Composable
fun RestrictionsPill(restrictions: SchoolRestrictions?, onClick: () -> Unit) {
    if (restrictions == null || restrictions.isEmpty) return
    val strongest = restrictions.active.minBy { it.kind.ordinal }.kind
    val color = restrictionColor(strongest)
    val closed = restrictions.closedCount
    val label = when {
        closed == 1 -> stringResource(R.string.restriction_pill_closed_one)
        closed > 1 -> stringResource(R.string.restriction_pill_closed_other, closed)
        strongest == RestrictionKind.REGULATED -> stringResource(R.string.restriction_kind_regulated)
        else -> stringResource(R.string.restriction_kind_notice)
    }
    Row(
        Modifier.clip(CircleShape).border(1.dp, color, CircleShape).clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RestrictionMark(strongest)
        Text(label.uppercase(), style = EyebrowTextStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp), color = color,
            maxLines = 1)
    }
}

/** "Cerrado hasta el 31 jul" / "Cerrado siempre" / "Regulado del 1 ene al 31 jul". */
@Composable
fun restrictionWhenText(r: Restriction): String {
    val kind = stringResource(kindLabel(r.kind))
    if (r.permanent) return stringResource(R.string.restriction_permanent, kind)
    val months = androidx.compose.ui.res.stringArrayResource(R.array.cal_months_short)
    val end = r.endDay?.let { d -> r.endMonth?.let { m -> "$d ${months[m - 1]}" } } ?: return kind
    return stringResource(R.string.restriction_until, kind, end)
}

private fun kindLabel(kind: RestrictionKind) = when (kind) {
    RestrictionKind.CLOSED -> R.string.restriction_kind_closed
    RestrictionKind.REGULATED -> R.string.restriction_kind_regulated
    RestrictionKind.NOTICE -> R.string.restriction_kind_notice
}

/** Una fila de la hoja: el "!", qué y hasta cuándo, dónde, motivo y fuente (tocable si tiene enlace). */
@Composable
fun RestrictionRow(r: Restriction, place: String?) {
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RestrictionMark(r.kind, size = 20)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(restrictionWhenText(r), color = restrictionColor(r.kind), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            place?.let { Text(it, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            Text(r.reason, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            if (r.yearly) Text(stringResource(R.string.restriction_yearly), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                stringResource(R.string.restriction_source, r.sourceName),
                color = if (r.sourceUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Mono, fontSize = 11.sp,
                modifier = if (r.sourceUrl != null) Modifier.clickable { runCatching { uri.openUri(r.sourceUrl!!) } } else Modifier
            )
        }
    }
}

/** Dónde se aplica, en palabras: "Sector Alcores" o "3 piedras de Alcores". */
@Composable
fun restrictionPlace(r: Restriction, blocks: List<Block>): String? {
    val sector = blocks.firstOrNull { it.id == r.sectorBlockId }?.name
    return when {
        r.blockIds.isEmpty() -> sector?.let { stringResource(R.string.restriction_place_sector, it) }
        r.blockIds.size == 1 -> blocks.firstOrNull { it.id == r.blockIds.first() }?.name
        else -> stringResource(R.string.restriction_place_blocks, r.blockIds.size) + (sector?.let { " · $it" } ?: "")
    }
}

/** Hoja con las restricciones (de la escuela entera o de un sector / piedra). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestrictionsSheet(items: List<Restriction>, blocks: List<Block>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.restriction_sheet_title).uppercase(), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.sortedBy { it.kind.ordinal }.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                RestrictionRow(r, restrictionPlace(r, blocks))
            }
            Text(stringResource(R.string.restriction_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        }
    }
}
