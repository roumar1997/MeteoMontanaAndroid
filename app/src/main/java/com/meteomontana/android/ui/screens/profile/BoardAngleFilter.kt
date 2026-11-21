package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Fila «Ángulos» (plegada por defecto) de un tablero con inclinaciones: al abrirla salen «Todos» y cada inclinación que hay en
 * [list] con su cuenta. Elegir una filtra el reparto por grado, el grado máximo y los bloques con nombre de ese tablero.
 * Devuelve la inclinación elegida (null = todas) para que quien llama filtre con [BoardAngles.filter].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardAngleFilter(kind: BoardKind, list: List<GymAscent>, selected: Int?, onSelect: (Int?) -> Unit) {
    val counts = BoardAngles.counts(list, kind)
    if (counts.isEmpty()) return
    var open by rememberSaveable(kind.name, "angles") { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).clickable { open = !open }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.gym_board_angles).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        selected?.let { Text("$it°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = terraFillColor()) }
        Row(Modifier.weight(1f)) {}
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AngleChip(stringResource(R.string.gym_board_angles_all), selected == null) { onSelect(null) }
        counts.forEach { (angle, n) -> AngleChip(stringResource(R.string.gym_board_angle_chip, angle, n), selected == angle) { onSelect(if (selected == angle) null else angle) } }
    }
}

@Composable
private fun AngleChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (on) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.clip(CircleShape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
