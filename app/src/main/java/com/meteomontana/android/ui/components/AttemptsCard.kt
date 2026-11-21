package com.meteomontana.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Attempts
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Tarjeta «🎯 ¿En cuántos intentos? · opcional»: chips grandes 1 (con ⚡, «1 · FLASH», dorado al elegirlo), 2, 3, 4 y «5+» (abre la lista
 * de 5 a 30). NADA viene elegido: sin tocar no se apunta ningun intento ni flash; tocar el elegido lo quita. Sirve en el registro de
 * boards y en la hoja «Hecho» de escuelas. Los chips se reparten el ancho (nada de medidas fijas).
 */
@Composable
fun AttemptsCard(selected: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎯 " + stringResource(R.string.attempts_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.attempts_optional), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 1.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlashChip(selected == Attempts.FLASH, Modifier.weight(2.4f)) { onChange(if (selected == Attempts.FLASH) null else Attempts.FLASH) }
            Attempts.QUICK.drop(1).forEach { n ->
                NumberChip(n.toString(), selected == n, Modifier.weight(1f)) { onChange(if (selected == n) null else n) }
            }
            MoreChip(selected, Modifier.weight(1.4f), onChange)
        }
        Text(stringResource(R.string.attempts_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlashChip(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 40.dp).clip(shape)
            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(FlashGoldStart, FlashGoldEnd))) else Modifier.background(MaterialTheme.colorScheme.background))
            .border(BorderStroke(if (on) 1.5.dp else 1.dp, if (on) Color.White else MaterialTheme.colorScheme.outline), shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.FlashOn, null, Modifier.size(15.dp), tint = if (on) FlashBrown else terraFillColor())
        Text(stringResource(R.string.attempts_flash_chip), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.5.sp, maxLines = 1,
            color = if (on) FlashBrown else MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
private fun NumberChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.heightIn(min = 40.dp).clip(shape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.background)
            .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (on) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}

/** «5+»: abre una lista de 5 a 30; elegido muestra el numero. */
@Composable
private fun MoreChip(selected: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val on = Attempts.isFivePlus(selected)
    val shape = RoundedCornerShape(50)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.background)
                .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, shape).clickable { open = true },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (on) selected.toString() else stringResource(R.string.attempts_more), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                color = if (on) Color.White else MaterialTheme.colorScheme.onBackground)
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 280.dp)) {
            Attempts.MORE.forEach { n ->
                DropdownMenuItem(text = { Text(n.toString(), fontWeight = if (n == selected) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onChange(if (selected == n) null else n) })
            }
        }
    }
}
