package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Attempts
import com.meteomontana.android.domain.model.gym.AttemptsStyleLink
import com.meteomontana.android.domain.model.gym.StyleAttempts

/**
 * Tarjeta unica «🎯 ¿Cómo te salió? · opcional» de la hoja «Hecho» de escuelas: pastillas «👁 A VISTA» (azul) y «⚡ FLASH» (dorada),
 * y debajo 2, 3, 4 y «5+» con «intentos». Eleccion unica y NADA elegido de inicio; tocar lo elegido lo quita. A VISTA y FLASH = a la
 * primera (1 intento); un numero = esos intentos sin estilo. La logica vive en [AttemptsStyleLink] (shared).
 */
@Composable
fun AscentStyleCard(value: StyleAttempts, onChange: (StyleAttempts) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val n = value.attempts?.takeIf { !value.flash && !value.onsight }
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎯 " + stringResource(R.string.ascent_how_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.attempts_optional), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 1.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OnsightChip(value.onsight, Modifier.weight(1f)) { onChange(AttemptsStyleLink.setOnsight(value, !value.onsight)) }
            FlashChip(value.flash, Modifier.weight(1f), stringResource(R.string.ascent_flash_pill)) { onChange(AttemptsStyleLink.setFlash(value, !value.flash)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Attempts.QUICK.drop(1).forEach { k ->
                NumberChip(k.toString(), n == k, Modifier.weight(1f)) { onChange(AttemptsStyleLink.pick(value, if (n == k) null else k)) }
            }
            MoreChip(n, Modifier.weight(1.4f)) { onChange(AttemptsStyleLink.pick(value, it)) }
            Text(stringResource(R.string.ascent_how_attempts), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1.3f))
        }
        Text(stringResource(R.string.ascent_how_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pastilla «👁 A VISTA»: azul con texto marino y contorno blanco al elegirla; apagada, como los demas chips. */
@Composable
private fun OnsightChip(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier.heightIn(min = 40.dp)
    Row(
        (if (on) base.shadow(6.dp, shape, ambientColor = OnsightBlueEnd, spotColor = OnsightBlueEnd) else base).clip(shape)
            .background(if (on) OnsightBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background))
            .border(if (on) 1.5.dp else 1.dp, if (on) Color.White else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp), tint = if (on) OnsightNavy else OnsightBlueEnd)
        Text(stringResource(R.string.onsight_badge), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.5.sp, maxLines = 1,
            color = if (on) OnsightNavy else MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 4.dp))
    }
}
