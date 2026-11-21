package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblemRepeats
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion
import com.meteomontana.android.ui.theme.terraFillColor

/** Lo que el registro de boards necesita para autocompletar el nombre del bloque mientras se escribe (sugerencias y avisos). */
internal class BoardNameAssist(
    val suggestions: List<BoardProblemSuggestion>,
    val onQuery: (BoardKind, String) -> Unit,
    val onClear: () -> Unit
) {
    companion object { val None = BoardNameAssist(emptyList(), { _, _ -> }, {}) }
}

/** Sugerencias de nombre (las propias primero y luego las de todas las personas): se pintan solo mientras se escribe. */
@Composable
internal fun NameSuggestions(suggestions: List<BoardProblemSuggestion>, onPick: (BoardProblemSuggestion) -> Unit) {
    if (suggestions.isEmpty()) return
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape)) {
        suggestions.forEach { s ->
            val meta = listOfNotNull(s.grade.uppercase(), s.angle?.let { "$it°" }, stringResource(R.string.gym_board_suggest_people, s.count)).joinToString(" · ")
            Column(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                Text(meta, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Cuantas veces se apunta de golpe el mismo bloque (entrenos de vueltas): − N +. */
@Composable
internal fun RepsStepper(reps: Int, onChange: (Int) -> Unit) {
    val minusCd = stringResource(R.string.gym_board_reps_minus_cd)
    val plusCd = stringResource(R.string.gym_board_reps_plus_cd)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.gym_board_reps_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        StepButton("−", reps > 1, Modifier.semantics { contentDescription = minusCd }) { onChange(BoardProblemRepeats.clampReps(reps - 1)) }
        Text("×$reps", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
        StepButton("+", reps < BoardProblemRepeats.MAX_REPS, Modifier.semantics { contentDescription = plusCd }) { onChange(BoardProblemRepeats.clampReps(reps + 1)) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        color = if (enabled) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.size(40.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick).padding(top = 6.dp)
    )
}
