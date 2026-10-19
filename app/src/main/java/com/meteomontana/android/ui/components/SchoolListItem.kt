package com.meteomontana.android.ui.components

import com.meteomontana.android.util.CatalogLabels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.scoreColor

/**
 * Tarjeta de una escuela en la lista (Álvaro, 2026-10-19, propuesta A; espejo de SchoolListCards.swift):
 *
 *   ╭──────────────────────────────────────────╮
 *   │ ( 60 )  El Burguillo [crashpad]        ☆ │
 *   │         [Bueno] Granito · 40 km [Roca seca]│
 *   │ ▆▆▆▆[▆]▆▆▆▆▆                              │
 *   │ 18 19 20 21 22 23 00 01 02 03            │
 *   │              Mejor hora: a las 21 h (67) │
 *   ╰──────────────────────────────────────────╯
 *
 * La nota en un anillo que se llena con su color; con días elegidos, el anillo es la nota del tramo y abajo van
 * los cuadraditos de cada día. Solo cambia cómo se ve: mismos datos y mismos gestos.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SchoolListItem(
    school: School,
    todayScore: Int? = null,
    hourlyScores: List<Int>? = null,
    distanceKm: Double? = null,
    dry: Boolean? = null,
    range: com.meteomontana.android.domain.model.RangeScore? = null,
    isFavorite: Boolean = false,
    selectedForCompare: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val value = range?.combinedScore ?: todayScore
    val shape = RoundedCornerShape(16.dp)
    // Lo que ACABAS de tocar en la ficha manda sobre lo que trajo el catálogo (Álvaro, 2026-09-05).
    val overrides by com.meteomontana.android.domain.util.ProcessionaryOverrideStore.observe()
        .collectAsState(initial = com.meteomontana.android.domain.util.ProcessionaryOverrideStore.currentValue())
    val processionaryAlertActive = overrides[school.id] ?: school.processionaryAlertActive

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(schoolCardBg())
            .border(
                if (selectedForCompare) 2.dp else 1.dp,
                if (selectedForCompare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreRing(value)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Nombre + iconos ocupan todo lo que sobra: la estrella queda pegada a la derecha (como iOS).
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = school.name,
                            style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val kind = com.meteomontana.android.domain.util.SchoolKind.from(school.style)
                        if (!kind.isEmpty) SchoolKindIcons(kind)
                    }
                    // Procesionaria: solo si ya se sabe Y estamos en su temporada (Álvaro, 2026-09-05).
                    if (processionaryAlertActive) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) { ProcessionaryIcon(tint = Color.White, modifier = Modifier.size(12.dp)) }
                    }
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) stringResource(R.string.school_list_item_quitar_de_favoritos)
                                             else stringResource(R.string.school_list_item_anadir_a_favoritos),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .let { m -> if (onToggleFavorite != null) m.clickable(onClick = onToggleFavorite) else m }
                            .padding(6.dp)
                    )
                }
                // Nota en palabras · roca y distancia · seca/mojada; se parten en dos líneas si no caben.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    if (value != null) SoftPill(niceScoreLabel(value), MaterialTheme.colorScheme.onSurfaceVariant, cumbrePillBg())
                    val meta = schoolMeta(school, distanceKm)
                    if (meta.isNotEmpty()) {
                        Text(meta, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (range == null && dry != null) {
                        val c = if (dry) okColor() else MaterialTheme.colorScheme.error
                        SoftPill(
                            stringResource(if (dry) R.string.schools_card_dry_rock else R.string.schools_card_wet_rock),
                            c, c.copy(alpha = 0.14f)
                        )
                    }
                }
            }
        }
        if (range != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRangeRow(range = range, modifier = Modifier.weight(1f))
                RainSummaryTag(range)
            }
        } else if (!hourlyScores.isNullOrEmpty()) {
            HourBars(hourlyScores.take(10))
        }
    }
}

/** Fondo de las tarjetas: un punto más claro que el fondo de la pantalla (espejo de iOS 0xFBFAF6 / 0x211F19). */
@Composable
private fun schoolCardBg(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFFBFAF6) else Color(0xFF211F19)

@Composable
internal fun cumbrePillBg(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) com.meteomontana.android.ui.theme.Paper else Color(0xFF2E2C25)

@Composable
private fun okColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) com.meteomontana.android.ui.theme.Ok else Color(0xFF7DA068)

/** «BUENO» → «Bueno», «MUY BUENO» → «Muy bueno». */
private fun niceScoreLabel(score: Int): String =
    CatalogLabels.scoreLabel(score).lowercase().replaceFirstChar { it.uppercase() }

/** «Granito · 26 km» (o la comunidad si no hay distancia). */
private fun schoolMeta(school: School, distanceKm: Double?): String = buildList {
    school.rockType?.takeIf { it.isNotBlank() }?.let { add(CatalogLabels.rock(it)) }
    if (distanceKm != null) add("${distanceKm.toInt()} km")
    else school.region?.takeIf { it.isNotBlank() }?.let { add(CatalogLabels.region(it)) }
}.joinToString(" · ")

/** Pastilla suave (fondo tintado, sin borde). */
@Composable
private fun SoftPill(text: String, fg: Color, bg: Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** La nota en un anillo que se llena con su color (sin nota: anillo vacío y «—»). */
@Composable
private fun ScoreRing(score: Int?) {
    val color = score?.let { scoreColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val track = cumbrePillBg()
    val label = score?.let { "$it, ${CatalogLabels.scoreLabel(it)}" } ?: stringResource(R.string.schools_card_no_score)
    Box(
        Modifier.size(58.dp).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (score != null) {
                val sweep = 360f * score.coerceIn(2, 100) / 100f
                drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(
            score?.toString() ?: "—",
            style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp),
            color = color
        )
    }
}

/** Las próximas horas como barras con su color, su hora debajo y la mejor hora dicha con palabras. */
@Composable
private fun HourBars(scores: List<Int>) {
    val now = java.time.LocalTime.now().hour
    val best = scores.indices.maxByOrNull { scores[it] } ?: 0
    val bestHour = (now + best) % 24
    val bestText = if (best == 0) stringResource(R.string.schools_card_best_now, scores[best])
                   else stringResource(R.string.schools_card_best_at, bestHour, scores[best])
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = bestText }) {
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom) {
            scores.forEachIndexed { i, v ->
                val barShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                Box(
                    Modifier.weight(1f)
                        .height((30f * v.coerceAtLeast(12) / 100f).coerceAtLeast(4f).dp)
                        .then(if (i == best) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, barShape) else Modifier)
                        .padding(if (i == best) 2.dp else 0.dp)
                        .clip(barShape)
                        .background(scoreColor(v))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // La hora bajo cada barra (la de ahora en negrita): sin adivinar las de en medio (Álvaro, 2026-10-19).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            scores.indices.forEach { i ->
                Text(
                    "%02d".format((now + i) % 24),
                    fontFamily = Mono, fontSize = 9.5.sp,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            bestText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Fila por día del tramo elegido: una celda por día con su score (color) y la inicial del día debajo. Los días con
 * lluvia llevan la inicial en rojo. Reutilizada también por el modo "Vías/Bloques" del buscador por grado.
 */
@Composable
fun DayRangeRow(
    range: com.meteomontana.android.domain.model.RangeScore,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        range.days.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scoreColor(d.score).copy(alpha = 0.18f))
                        .border(
                            1.dp,
                            if (d.rainy) MaterialTheme.colorScheme.error else scoreColor(d.score),
                            RoundedCornerShape(2.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = d.score.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold, fontSize = 11.sp
                        ),
                        color = scoreColor(d.score)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = weekdayLetter(d.date),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (d.rainy) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Resumen de lluvia del tramo: qué días llueve (iniciales) y máximo de mm. */
@Composable
private fun RainSummaryTag(range: com.meteomontana.android.domain.model.RangeScore) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (range.rainDays == 0) {
            Text(
                text = stringResource(R.string.school_list_item_v3_sin_lluvia),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
                ),
                color = okColor()
            )
        } else {
            val rainyDays = range.days.filter { it.rainy }.joinToString(" ") { weekdayLetter(it.date) }
            Text(
                text = "LLUEVE $rainyDays",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.error
            )
            if (range.maxRainMm > 0.0) {
                Text(
                    text = "máx %.1f mm".format(range.maxRainMm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "2026-06-17" → "L"/"M"/"X"/"J"/"V"/"S"/"D". */
private fun weekdayLetter(iso: String): String = try {
    val labels = arrayOf("L", "M", "X", "J", "V", "S", "D")  // ISO 1=lunes
    labels[java.time.LocalDate.parse(iso).dayOfWeek.value - 1]
} catch (_: Throwable) { iso.takeLast(2) }
