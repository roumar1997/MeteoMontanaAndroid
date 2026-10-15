package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.model.HeightSplit
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.gradeChipColor
import com.meteomontana.android.ui.theme.gradeChipStyle

// Votar el grado teniendo en cuenta la ALTURA (2026-10-15). Espejo de GradeHeightViews.swift de iOS: cada persona elige
// UNA vez si mide más o menos de 1,70 m (privado, en la mochilita) y sus votos se agrupan con los de su altura. Si los
// dos grupos votan un grado muy distinto, el grado se "parte" en dos colores.

/** Los grados sueltos de un grado, en minúsculas ("6b+/6c" → ["6b+", "6c"]; "6b" → ["6b"]; vacío → []). */
fun gradeParts(grade: String?): List<String> =
    grade?.trim()?.lowercase()?.split("/")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/**
 * ¿Cuadra este grado con los filtros elegidos? Sin filtros, todo. Un grado doble cuadra si CUALQUIERA de sus dos grados
 * está elegido (así "6b+/6c" sale al filtrar por 6b+ y también al filtrar por 6c).
 */
fun gradeMatches(grade: String?, anyOf: Set<String>): Boolean =
    anyOf.isEmpty() || gradeParts(grade).any { it in anyOf }

/** Lo justo para pintar la pastilla del grado sin abrir la hoja: cuántos votos hay y si la altura parte la opinión. */
data class GradeBadge(val count: Int, val split: HeightSplit?) {
    companion object {
        fun of(g: GradeSummary) = GradeBadge(g.votes.values.sum(), g.heightSplit)
    }
}

/** "14 votos · ver y votar" · "14 votos · según la altura" · "Sin votos · votar". */
@Composable
fun gradeBadgeCaption(badge: GradeBadge?): String {
    val count = badge?.count ?: 0
    if (count == 0) return stringResource(R.string.grade_badge_no_votes)
    val votes = if (count == 1) stringResource(R.string.grade_badge_one_vote)
                else stringResource(R.string.grade_badge_votes, count)
    return votes + if (badge?.split == null) stringResource(R.string.grade_badge_view_and_vote)
                   else stringResource(R.string.grade_badge_by_height)
}

/** Un trozo de pastilla: el grado sobre SU color (texto negro si el color es claro). */
@Composable
private fun GradeSegment(grade: String, fontSize: Int, hPad: Int, vPad: Int) {
    val dark = gradeChipStyle(grade).dark
    // fillMaxHeight: el color llena TODA la altura de la fila, también cuando otro trozo (la flecha) es más alto.
    Box(
        Modifier.fillMaxHeight().background(gradeChipColor(grade)).padding(horizontal = hPad.dp, vertical = vPad.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            grade,
            style = EyebrowTextStyle.copy(fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
            color = if (dark) Color.Black else Color.White
        )
    }
}

/**
 * El grado como pastilla con su COLOR de siempre y una flecha clara en un círculo (se lee como botón). Si la altura parte
 * la opinión, son dos colores seguidos.
 */
@Composable
fun GradeChip(grade: String, split: HeightSplit?, onClick: () -> Unit) {
    val last = split?.shortGrade ?: grade
    val cd = stringResource(R.string.grade_chip_cd, grade)
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (split != null) {
            GradeSegment(split.tallGrade, 12, 10, 6)
            GradeSegment(split.shortGrade, 12, 8, 6)
        } else {
            GradeSegment(grade, 12, 10, 6)
        }
        Box(
            Modifier.fillMaxHeight().background(gradeChipColor(last)).background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("⌄", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (gradeChipStyle(last).dark) Color.Black else Color.White)
        }
    }
}

/** Texto de una altura ("+ 1,70", "− 1,70" o "sin decir"). */
@Composable
fun heightLabel(group: HeightGroup): String = when (group) {
    HeightGroup.TALL -> "+ 1,70"
    HeightGroup.SHORT -> "− 1,70"
    else -> stringResource(R.string.height_unsaid)
}

/** "+ 1,70 · − 1,70 · Prefiero no decirlo": la altura en tres opciones. */
@Composable
fun HeightChoiceView(selected: HeightGroup?, onSelect: (HeightGroup) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HeightOption(HeightGroup.TALL, "+ 1,70", selected, onSelect, Modifier.weight(1f))
        HeightOption(HeightGroup.SHORT, "− 1,70", selected, onSelect, Modifier.weight(1f))
        HeightOption(HeightGroup.UNSPECIFIED, stringResource(R.string.height_prefer_not), selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun HeightOption(group: HeightGroup, label: String, selected: HeightGroup?, onSelect: (HeightGroup) -> Unit, modifier: Modifier) {
    val on = selected == group
    Box(
        modifier
            .background(if (on) Terra else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onSelect(group) }
            .padding(horizontal = 2.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = EyebrowTextStyle.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** El aviso de la hoja de votar cuando todavía no has dicho tu altura: se pide UNA vez, dentro del propio voto. */
@Composable
fun HeightPromptCard(selected: HeightGroup?, onSelect: (HeightGroup) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Terra.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.5.dp, Terra, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.height_prompt_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Terra)
        Text(stringResource(R.string.height_prompt_body), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HeightChoiceView(selected, onSelect)
    }
}

/** Los votos de un grado separados por la altura de quien vota: "MÁS DE 1,70 · 9 VOTOS" + sus barras, etc. */
@Composable
fun HeightVoteGroups(summary: GradeSummary) {
    val groups = listOf(
        "TALL" to stringResource(R.string.height_group_tall),
        "SHORT" to stringResource(R.string.height_group_short),
        "NONE" to stringResource(R.string.height_group_none)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (key, title) ->
            val votes = summary.votesByHeight[key].orEmpty()
            val total = votes.values.sum()
            if (total > 0) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val count = if (total == 1) stringResource(R.string.height_votes_one_caps)
                                else stringResource(R.string.height_votes_caps, total)
                    Text("$title · $count", style = EyebrowTextStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VoteBars(votes, summary.displayedGrade, summary.myVote) { g ->
                        Color(com.meteomontana.android.domain.util.gradeArgb(g).first.toInt())
                    }
                }
            }
        }
    }
}

/** ¿Hay votos separados por altura? Si todos son "sin dato" se enseña la barra única de siempre. */
fun hasHeightData(summary: GradeSummary): Boolean =
    (summary.votesByHeight["TALL"].orEmpty().values.sum() + summary.votesByHeight["SHORT"].orEmpty().values.sum()) > 0

