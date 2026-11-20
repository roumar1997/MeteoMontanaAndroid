package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.ui.screens.gyms.GymPickerSheet
import com.meteomontana.android.ui.screens.gyms.GymTrainSheet
import com.meteomontana.android.ui.theme.terraFillColor
import kotlinx.datetime.LocalDate
import java.util.Locale

/** Un tramo de la barra de colores: un grado o el color de una escala, con cuántas veces y su orden de dificultad. */
data class BarSeg(val grade: String?, val hex: String?, val label: String, val count: Int, val order: Int)

/**
 * «+ APUNTAR ENTRENO»: abre el selector de rocódromo (Hoy, Ayer u otro día) y después la hoja de entreno de ese día.
 * [onSaved] avisa al guardar para que el perfil relea sus números.
 */
@Composable
internal fun LogWorkoutButton(modifier: Modifier = Modifier, onSaved: () -> Unit = {}) {
    var picking by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Pair<Gym, LocalDate>?>(null) }
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(terraFillColor()).clickable { picking = true }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.gym_pick_eyebrow).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(end = 2.dp))
    }
    if (picking) GymPickerSheet(onPick = { gym, day -> picking = false; sheetFor = gym to day }, onDismiss = { picking = false })
    sheetFor?.let { (gym, day) ->
        val label = java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
        GymTrainSheet(gym, label, onDismiss = { sheetFor = null; onSaved() }, date = day)
    }
}
