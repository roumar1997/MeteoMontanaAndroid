package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.BoardGradeSlice
import com.meteomontana.android.domain.model.gym.BoardGrades
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.gradeChipStyle

/**
 * «GRADOS» row of a board (§28), right under its header. Collapsed: label, stacked colour bar and «Top 7B+». Expanded: one
 * pill + bar + count row per grade; tapping a grade filters the problem list (again = clear). Mirror of `BoardGradeRow.swift`.
 */
@Composable
internal fun BoardGradeRow(kind: BoardKind, items: List<GymAscent>, selected: String?, onSelect: (String?) -> Unit) {
    val slices = BoardGrades.slices(items)
    if (slices.isEmpty()) return
    var open by rememberSaveable(kind.name, "grades") { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.gym_board_grades_label).uppercase(), style = EyebrowTextStyle, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StackedBar(slices, Modifier.weight(1f))
            BoardGrades.top(slices)?.let { top ->
                val topColor = gradeChipStyle(top).stroke
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.gym_board_grades_top)); append(" ")
                        withStyle(SpanStyle(color = topColor, fontWeight = FontWeight.Bold)) { append(top.uppercase()) }
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.gym_board_grades_toggle_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            val top = BoardGrades.maxCount(slices)
            slices.forEach { GradeLine(it, top, selected, onSelect) }
            Text(stringResource(R.string.gym_board_grades_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StackedBar(slices: List<BoardGradeSlice>, modifier: Modifier = Modifier) {
    Row(modifier.height(9.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        slices.forEach { s ->
            Spacer(Modifier.weight(s.share.coerceAtLeast(0.01f)).fillMaxHeight().background(gradeChipStyle(s.grade).stroke))
        }
    }
}

@Composable
private fun GradeLine(s: BoardGradeSlice, top: Int, selected: String?, onSelect: (String?) -> Unit) {
    val st = gradeChipStyle(s.grade)
    val on = BoardGrades.isSelected(selected, s.grade)
    val dim = selected != null && !on
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable { onSelect(BoardGrades.toggle(selected, s.grade)) }.alpha(if (dim) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            s.grade.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
            modifier = Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).background(st.stroke)
                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp)) else Modifier)
                .padding(vertical = 4.dp)
        )
        Bar(s.count.toFloat() / top, st.stroke, Modifier.weight(1f))
        Text(
            s.count.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground)
    }
}
