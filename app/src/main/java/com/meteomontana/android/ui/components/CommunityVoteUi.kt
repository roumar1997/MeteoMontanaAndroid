package com.meteomontana.android.ui.components


import com.meteomontana.android.util.CatalogLabels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import com.meteomontana.android.domain.model.HeightGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * UI de la votación comunitaria (C2/C5). Regla de diseño (DESIGN.md): todo lo
 * VOTABLE lleva el mismo lenguaje — chip con borde DISCONTINUO terra + ▾.
 * Se aprende una vez y se reconoce en toda la app.
 */

val ASPECTS = com.meteomontana.android.domain.util.Aspect.ALL

/** Chip pulsable con borde discontinuo terra + ▾ (orientación, grado, año…). */
@Composable
fun VotableChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
    Row(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = Terra, style = Stroke(width = 3f, pathEffect = dash),
                    cornerRadius = CornerRadius(50f, 50f)
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, style = EyebrowTextStyle.copy(fontSize = 11.sp), color = Terra)
        Text("▾", fontSize = 10.sp, color = Terra)
    }
}

/**
 * Barras de votos (compartidas por orientación y grado). El orden lo da shared (`VoteOrdering`, el mismo que iOS): más
 * votado primero y, en empate, por grado o por nombre, para que las barras NO cambien de sitio entre aperturas.
 * [barColor] = el color de cada opción (los grados llevan el suyo, como en la app); null = terracota.
 */
@Composable
internal fun VoteBars(votes: Map<String, Int>, highlight: String?, myVote: String?, barColor: ((String) -> Color)? = null) {
    val max = (votes.values.maxOrNull() ?: 1).coerceAtLeast(1)
    com.meteomontana.android.domain.usecase.community.VoteOrdering.ordered(votes).forEach { (option, count) ->
        val color = barColor?.invoke(option) ?: Terra
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                option,
                style = EyebrowTextStyle.copy(fontSize = 12.sp),
                color = if (option == highlight) Terra else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 38.dp)
            )
            Box(Modifier.weight(1f).height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))) {
                Box(Modifier.fillMaxWidth(count / max.toFloat()).height(14.dp)
                    .background(if (option == highlight) color else color.copy(alpha = 0.55f),
                        RoundedCornerShape(7.dp)))
            }
            Text(
                "$count" + if (option == myVote) stringResource(R.string.community_vote_ui_v3_tu) else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Contenido del diálogo de votar ORIENTACIÓN. */
@Composable
fun OrientationVoteContent(
    summary: OrientationSummary?,
    onVote: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.community_vote_ui_hacia_donde_mira_esta), style = EyebrowTextStyle, color = Terra)
        Text(
            stringResource(R.string.community_vote_ui_v2_vota_la_comunidad_se_muestra),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        if (summary != null && summary.votes.isNotEmpty()) {
            VoteBars(summary.votes, summary.consensus, summary.myVote)
        } else {
            Text(stringResource(R.string.community_vote_ui_sin_votos_todavia_se),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        // Brujula: INFORMA, no decide. Estando en la roca dice hacia donde
        // miras; el punto cardinal lo eliges tu tocando su chip, porque la
        // brujula del movil se descalibra con facilidad.
        val rumbo = rememberDeviceHeading(stepDegrees = 2f)
        if (rumbo != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   modifier = Modifier.fillMaxWidth()) {
                CompassDial(rumbo)
                Text(
                    stringResource(R.string.community_vote_ui_v2_estas_mirando_al,
                        CatalogLabels.aspect(com.meteomontana.android.domain.util.Aspect.fromDegrees(rumbo)),
                        com.meteomontana.android.domain.util.Aspect.degreesLabel(rumbo)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ASPECTS.take(4).forEach { a -> AspectChip(a, summary?.myVote == a) { onVote(a) } }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ASPECTS.drop(4).forEach { a -> AspectChip(a, summary?.myVote == a) { onVote(a) } }
        }
    }
}

@Composable
private fun AspectChip(aspect: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) Terra else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(6.dp)
            )
            .border(1.dp, if (selected) Terra else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            CatalogLabels.aspect(aspect) + if (selected) " ✓" else "",
            style = EyebrowTextStyle.copy(fontSize = 11.sp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Todos los grados en orden (4a, 4a+, ... 8c+): sirve para saber cuáles son contiguos. */
private val SUFFIXES = listOf("a", "a+", "b", "b+", "c", "c+")
private val ALL_GRADES = listOf("4", "5", "6", "7", "8").flatMap { n -> SUFFIXES.map { n + it } }

/**
 * Qué voto queda al pulsar [g]: si ya está elegido, se quita de un voto doble (queda el otro); si es el contiguo del
 * grado elegido, el voto pasa a ser el PAR (6b/6b+, de menor a mayor); si no, es el nuevo voto.
 */
internal fun nextGradeVote(g: String, current: String?): String {
    val parts = gradeParts(current)
    val gi = ALL_GRADES.indexOf(g)
    if (gi < 0) return g
    if (g in parts) return if (parts.size == 2) (if (parts[0] == g) parts[1] else parts[0]) else g
    if (parts.size == 1) {
        val pi = ALL_GRADES.indexOf(parts[0])
        if (pi >= 0 && kotlin.math.abs(pi - gi) == 1) return if (pi < gi) parts[0] + "/" + g else g + "/" + parts[0]
    }
    return g
}

/** Contenido del diálogo de votar GRADO (C5), con la altura de quien vota (espejo de GradeVoteSheet de iOS). */
@Composable
fun GradeVoteContent(
    summary: GradeSummary?,
    canVote: Boolean,
    myHeight: HeightGroup?,
    onSetHeight: (HeightGroup) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    // "Cambiar" mi altura: vuelve a enseñar las tres opciones.
    var changingHeight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Lo elegido pero AÚN NO guardado: pulsar grados solo cambia esto; el voto se guarda con GUARDAR.
    var edited by androidx.compose.runtime.remember(summary?.lineId, summary?.myVote) { androidx.compose.runtime.mutableStateOf(false) }
    var draft by androidx.compose.runtime.remember(summary?.lineId, summary?.myVote) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.community_vote_ui_que_grado_le_das), style = EyebrowTextStyle, color = Terra)
        Text(
            if (canVote)
                stringResource(R.string.community_vote_ui_v3_el_grado_que_se_muestra)
            else
                stringResource(R.string.community_vote_ui_v3_solo_puede_votar_quien_la),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val s = summary
        if (s == null) {
            SkeletonList(count = 4, dividers = false) {
                androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 6.dp)) {
                    Bone(44.dp, 14.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) { Bone(null, 14.dp) }
                }
            }
            return@Column
        }
        if (s.heightSplit != null) {
            Text(
                stringResource(R.string.grade_split_note),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Terra,
                modifier = Modifier.fillMaxWidth()
                    .background(Terra.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }
        if (hasHeightData(s)) HeightVoteGroups(s)
        else if (s.votes.isNotEmpty()) VoteBars(s.votes, s.displayedGrade, s.myVote) { grade ->
            Color(com.meteomontana.android.domain.util.gradeArgb(grade).first.toInt())
        }
        Row(
            Modifier.fillMaxWidth()
                .background(Terra.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.community_vote_ui_v2_mostrado), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (s.displayedGrade ?: "—") +
                    (s.setterGrade?.takeIf { it != s.displayedGrade }
                        ?.let { stringResource(R.string.community_vote_ui_v4_equipador, it) } ?: ""),
                style = EyebrowTextStyle.copy(fontSize = 12.sp), color = Terra
            )
        }
        if (canVote) {
            // La altura se pide UNA vez, aquí mismo: hasta que se elige, los grados están apagados.
            if (myHeight == null || changingHeight) {
                HeightPromptCard(myHeight) { group -> onSetHeight(group); changingHeight = false }
            } else {
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.height_yours, heightLabel(myHeight)),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.height_change), fontSize = 12.sp, color = Terra,
                        modifier = Modifier.clickable { changingHeight = true })
                }
            }
            GradePicker(
                current = if (edited) draft else s.myVote, enabled = myHeight != null,
                onPick = { g -> draft = nextGradeVote(g, if (edited) draft else s.myVote); edited = true }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    // Acciones FIJAS abajo: siempre se ven sin hacer scroll.
    val canSave = canVote && myHeight != null && edited && draft != null && draft != summary?.myVote
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canVote) {
            Text(
                stringResource(R.string.grade_vote_save), style = EyebrowTextStyle.copy(fontSize = 12.sp), color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer(alpha = if (canSave) 1f else 0.38f)
                    .background(Terra, RoundedCornerShape(50))
                    .clickable(enabled = canSave) { draft?.let(onSave) }
                    .padding(vertical = 12.dp)
            )
            if (summary?.myVote != null && myHeight != null) {
                Text(
                    stringResource(R.string.grade_vote_remove), style = EyebrowTextStyle.copy(fontSize = 11.sp), color = Terra,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Terra, RoundedCornerShape(50))
                        .clickable(onClick = onRemove)
                        .padding(vertical = 10.dp)
                )
            }
        }
        Text(
            stringResource(R.string.block_detail_dialog_v2_cerrar), style = EyebrowTextStyle, color = Terra,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(vertical = 8.dp)
        )
    }
    }
}


/**
 * Selector de grado francés: rejilla completa. Cada botón lleva el COLOR de su grado; el elegido (o los DOS, si dudas)
 * va relleno. Apagado hasta que se elige la altura.
 */
@Composable
private fun GradePicker(current: String?, enabled: Boolean, onPick: (String) -> Unit) {
    Column(Modifier.graphicsLayer(alpha = if (enabled) 1f else 0.38f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.community_vote_ui_v2_tu_voto), style = EyebrowTextStyle.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!enabled) {
            Text(stringResource(R.string.height_pick_to_vote), fontSize = 12.sp, color = Terra)
        }
        Text(stringResource(R.string.grade_vote_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val mine = gradeParts(current)
        listOf("4", "5", "6", "7", "8").forEach { n ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SUFFIXES.forEach { suf ->
                    val g = n + suf
                    val selected = g in mine
                    val style = com.meteomontana.android.ui.theme.gradeChipStyle(g)
                    val accent = if (style.dark) MaterialTheme.colorScheme.onSurface else style.stroke
                    Box(
                        Modifier.weight(1f)
                            .background(if (selected) com.meteomontana.android.ui.theme.gradeChipColor(g)
                                        else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, if (selected && !style.dark) Color.Transparent else accent, RoundedCornerShape(6.dp))
                            .clickable(enabled = enabled) { onPick(g) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            g, style = EyebrowTextStyle.copy(fontSize = 11.sp),
                            color = if (selected) (if (style.dark) Color.Black else Color.White) else accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Tira horaria de sol (amarillo = al sol, azul tinta = sombra). */
@Composable
fun SunStrip(sun: SunHours, modifier: Modifier = Modifier) {
    if (sun.hours.isEmpty()) return
    val sunColor = Color(0xFFE8B84B)
    val shadeColor = Color(0xFF3D4A5C)
    Column(modifier) {
        // Titulo + leyenda EN LA MISMA FILA (feedback N1: aprovechar el espacio).
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.community_vote_ui_sol_en_esta_pared), style = EyebrowTextStyle.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(Color(0xFFE8B84B), stringResource(R.string.w_sun)); LegendDot(Color(0xFF3D4A5C), stringResource(R.string.w_shade))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            sun.hours.forEach { h ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(18.dp)
                        .background(if (h.inSun) sunColor else shadeColor, RoundedCornerShape(4.dp)))
                    val hour = h.time.substringAfter('T').take(2).trimStart('0').ifEmpty { "0" }
                    Text(hour, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(9.dp).height(9.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
