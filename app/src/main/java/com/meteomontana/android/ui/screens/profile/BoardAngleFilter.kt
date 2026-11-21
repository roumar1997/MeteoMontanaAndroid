package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.BoardAngles
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Fila «ÁNGULOS» siempre visible de un tablero con inclinaciones: etiqueta y fila de chips con scroll horizontal («Todos» y cada
 * inclinación de [list]; arriba el ángulo y debajo, pequeña, la cuenta). Elegir una filtra el reparto por grado, el grado máximo y los
 * bloques con nombre de ese tablero. Devuelve la inclinación elegida (null = todas) para filtrar con [BoardAngles.filter].
 */
@Composable
internal fun BoardAngleFilter(kind: BoardKind, list: List<GymAscent>, selected: Int?, onSelect: (Int?) -> Unit) {
    val counts = BoardAngles.counts(list, kind)
    if (counts.isEmpty()) return
    val total = BoardAngles.filter(list, kind, null).sumOf { it.count }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.gym_board_angle_tag), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AngleChip(stringResource(R.string.gym_board_angles_all), pluralStringResource(R.plurals.gym_board_blocks_n, total, total), selected == null) { onSelect(null) }
            counts.forEach { (angle, n) -> AngleChip("$angle°", n.toString(), selected == angle) { onSelect(if (selected == angle) null else angle) } }
        }
    }
}

@Composable
private fun AngleChip(top: String, bottom: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.clip(shape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(top, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (on) Color.White else MaterialTheme.colorScheme.onBackground)
        Text(bottom, fontSize = 10.sp, maxLines = 1, color = if (on) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
