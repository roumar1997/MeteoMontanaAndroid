package com.meteomontana.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.util.GradeFilterResult
import com.meteomontana.android.domain.util.GradeMatch
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Ink
import com.meteomontana.android.ui.theme.Ink2
import com.meteomontana.android.ui.theme.Ink3
import com.meteomontana.android.ui.theme.Paper
import com.meteomontana.android.ui.theme.Rule
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade

// Filtro LOCAL por grado dentro de una escuela — ver BLOCK_SEARCH_DESIGN.md §7.
// Espejo de GradeFilterBar.swift (iOS): chips multi-selección con la paleta
// de grados de la app (mismo patrón que el diario), resultados agrupados por
// grado y colapsables, tocar una vía abre su piedra. La lógica de qué
// piedra/vía cae en la selección vive en shared (GradeFilter.kt).

@Composable
fun GradeFilterBar(
    selectedGrades: Set<String>,
    onSelectedGradesChange: (Set<String>) -> Unit,
    availableGrades: List<String>,
    result: GradeFilterResult,
    onSelectLine: (GradeMatch) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var openGroups by remember { mutableStateOf(setOf<String>()) }
    val isActive = selectedGrades.isNotEmpty()

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null,
                tint = if (isActive) Terra else Ink3, modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("FILTRAR POR GRADO", style = EyebrowTextStyle.copy(fontSize = 10.sp),
                color = if (isActive) Terra else Ink3)
            Spacer(Modifier.weight(1f))
            if (isActive) {
                Text("QUITAR TODO", style = EyebrowTextStyle.copy(fontSize = 9.sp), color = Ink3,
                    modifier = Modifier.clickable { onSelectedGradesChange(emptySet()) })
                Spacer(Modifier.width(10.dp))
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null, tint = Ink3
            )
        }

        if (expanded) {
            if (availableGrades.isEmpty()) {
                Text("Esta escuela todavía no tiene vías con grado.",
                    fontSize = 12.sp, color = Ink3,
                    modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    items(availableGrades) { g ->
                        GradeChip(
                            grade = g,
                            active = selectedGrades.contains(g),
                            onClick = {
                                onSelectedGradesChange(
                                    if (selectedGrades.contains(g)) selectedGrades - g else selectedGrades + g
                                )
                            }
                        )
                    }
                }

                if (isActive) {
                    Text(
                        "Mostrando ${result.matchingLines} vías de ${result.totalLines}",
                        fontSize = 12.sp, color = Ink2,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        result.groups.forEach { (grade, matches) ->
                            GradeGroup(
                                grade = grade,
                                matches = matches,
                                open = openGroups.contains(grade),
                                onToggle = {
                                    openGroups = if (openGroups.contains(grade)) openGroups - grade
                                    else openGroups + grade
                                },
                                onSelectLine = onSelectLine
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (expanded) 10.dp else 0.dp))
    }
}

@Composable
private fun GradeChip(grade: String, active: Boolean, onClick: () -> Unit) {
    val accent = colorForGrade(grade).let { if (it == Color.White) Ink else it }
    Row(
        Modifier
            .background(if (active) accent else Paper, RoundedCornerShape(2.dp))
            .border(1.dp, accent, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(grade.lowercase(), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (active) Color.White else accent)
    }
}

@Composable
private fun GradeGroup(
    grade: String,
    matches: List<GradeMatch>,
    open: Boolean,
    onToggle: () -> Unit,
    onSelectLine: (GradeMatch) -> Unit
) {
    val accent = colorForGrade(grade).let { if (it == Color.White) Ink else it }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(Modifier
                .background(accent, RoundedCornerShape(2.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(grade.lowercase(), fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (matches.size == 1) "1 vía" else "${matches.size} vías", fontSize = 12.sp, color = Ink3)
            Spacer(Modifier.weight(1f))
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                contentDescription = null, tint = Ink3
            )
        }
        if (open) {
            Column {
                matches.forEach { match ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLine(match) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(match.lineName, fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Ink)
                            Text(match.blockName, fontSize = 11.sp, color = Ink3)
                        }
                        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = Terra)
                    }
                    HorizontalDivider(color = Rule)
                }
            }
        }
    }
}
