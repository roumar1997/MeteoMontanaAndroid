package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Attempts
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.BoardBlockDay
import com.meteomontana.android.domain.model.gym.BoardBlockRow
import androidx.compose.foundation.clickable
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.FlashBadge
import com.meteomontana.android.ui.components.attemptsText
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.screens.gyms.boardName
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.gradeChipStyle

/**
 * Detalle de un bloque con nombre: cada dia en que se hizo (el mas reciente primero) con sus repeticiones y, en pequeno,
 * el rocodromo. Sin nombre: los de ese grado. En el perfil propio, el ⋯ de cada dia lo edita o lo borra (§26).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NamedProblemSheet(
    p: BoardBlockRow, summary: GymSummary, angle: Int? = null,
    /** Own profile: the ⋯ of each day edits it or deletes it (§26). */
    onEditDay: ((BoardBlockDay) -> Unit)? = null, onDeleteDay: ((BoardBlockDay) -> Unit)? = null, onDismiss: () -> Unit
) {
    val st = gradeChipStyle(p.grade.substringBefore('/'))
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, shape = CumbreSheetShape
    ) {
        Column(Modifier.fillMaxWidth().cumbreSheetSurface().navigationBarsPadding().padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    p.grade.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(st.stroke).padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Text(p.name ?: stringResource(R.string.gym_board_unnamed), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.common_close), color = terraFillColor(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val best = p.bestAttempts
            val summaryLine = listOfNotNull(
                boardName(p.board), angle?.let { "$it°" }, pluralStringResource(R.plurals.gym_board_detail_reps, p.dates.size, p.reps, p.dates.size),
                if (Attempts.isFlash(best)) stringResource(R.string.gym_board_flash_line) else best?.let { stringResource(R.string.gym_board_best, attemptsText(it)) }
            ).joinToString(" · ")
            Text(summaryLine, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.days.forEach { d ->
                    val shape = RoundedCornerShape(14.dp)
                    val gym = if (d.gymId == GymSession.NO_GYM) stringResource(R.string.gym_board_no_gym) else summary.byGym.firstOrNull { it.gymId == d.gymId }?.name
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dayLabel(d.date), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                            if (gym != null) Text(gym, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.gym_board_reps_badge, d.reps), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                            if (Attempts.isFlash(d.attempts)) FlashBadge()
                            else d.attempts?.let { Text(stringResource(R.string.gym_board_in_attempts, attemptsText(it)), fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (onEditDay != null || onDeleteDay != null) com.meteomontana.android.ui.components.EntryMoreMenu(
                            onEditDay?.let { e -> { e(d) } }, onDeleteDay?.let { del -> { del(d) } },
                            deleteLabel = stringResource(R.string.gym_board_delete)
                        )
                    }
                }
            }
        }
    }
}
