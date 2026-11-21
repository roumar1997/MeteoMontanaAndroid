package com.meteomontana.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R

/** Colores del distintivo dorado de FLASH (iOS usa los mismos). */
internal val FlashGoldStart = Color(0xFFF6C744)
internal val FlashGoldEnd = Color(0xFFF59E0B)
internal val FlashBrown = Color(0xFF3B2300)

/**
 * Distintivo dorado «⚡ FLASH» (degradado F6C744 a F59E0B, texto marron oscuro, contorno blanco y brillo suave). Es EL sitio donde la
 * app dice «Al flash»: perfil, buscador, fichas y feed lo usan en lugar de pintar su propio texto.
 */
@Composable
fun FlashBadge(modifier: Modifier = Modifier, label: String = stringResource(R.string.flash_badge), large: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.shadow(if (large) 6.dp else 4.dp, shape, ambientColor = FlashGoldEnd, spotColor = FlashGoldEnd)
            .clip(shape).background(Brush.horizontalGradient(listOf(FlashGoldStart, FlashGoldEnd)))
            .border(BorderStroke(1.5.dp, Color.White), shape)
            .padding(horizontal = if (large) 14.dp else 8.dp, vertical = if (large) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(Icons.Outlined.FlashOn, null, Modifier.size(if (large) 16.dp else 11.dp), tint = FlashBrown)
        Text(label, color = FlashBrown, fontWeight = FontWeight.ExtraBold, fontSize = if (large) 14.sp else 10.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

/** Pastilla naranja «🎯 N INTENTOS» para mas de un intento (con un intento es [FlashBadge]). */
@Composable
fun AttemptsPill(attempts: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        stringResource(R.string.attempts_pill, attempts), color = Color(0xFF9A3412), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
        letterSpacing = 0.8.sp, maxLines = 1,
        modifier = modifier.clip(shape).background(Color(0xFFFBE3D6)).border(1.dp, Color(0xFFC2410C), shape).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Lo que se dice de los intentos en una fila: FLASH si fue a la primera, «N INTENTOS» si hubo mas, nada si no se dijo. */
@Composable
fun AttemptsBadge(attempts: Int?, modifier: Modifier = Modifier) {
    when {
        com.meteomontana.android.domain.model.gym.Attempts.isFlash(attempts) -> FlashBadge(modifier)
        com.meteomontana.android.domain.model.gym.Attempts.showsCount(attempts) -> AttemptsPill(attempts!!, modifier)
    }
}

/** «N intentos» en texto gris pequeno (filas de boards); vacio si no hay mas de uno. */
@Composable
fun attemptsText(attempts: Int): String = pluralStringResource(R.plurals.attempts_n, attempts, attempts)
