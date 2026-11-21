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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R

/** Colores del distintivo azul de A VISTA (iOS usa los mismos). */
internal val OnsightBlueStart = Color(0xFF5EC8E5)
internal val OnsightBlueEnd = Color(0xFF2F80ED)
internal val OnsightNavy = Color(0xFF062A52)

/** Fondo del distintivo A VISTA: degradado azul. */
internal val OnsightBrush: Brush get() = Brush.horizontalGradient(listOf(OnsightBlueStart, OnsightBlueEnd))

/**
 * Distintivo azul «👁 A VISTA» (degradado 5EC8E5 a 2F80ED, texto azul marino 062A52, contorno blanco y brillo azul suave). Hermano de
 * [FlashBadge]: es EL sitio donde la app dice «A vista» (filas de Mis bloques/Mis vías y chip del filtro de estilo).
 */
@Composable
fun OnsightBadge(modifier: Modifier = Modifier, label: String = stringResource(R.string.onsight_badge), large: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.shadow(if (large) 6.dp else 4.dp, shape, ambientColor = OnsightBlueEnd, spotColor = OnsightBlueEnd)
            .clip(shape).background(OnsightBrush)
            .border(BorderStroke(1.5.dp, Color.White), shape)
            .padding(horizontal = if (large) 14.dp else 8.dp, vertical = if (large) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(Icons.Outlined.Visibility, null, Modifier.size(if (large) 16.dp else 11.dp), tint = OnsightNavy)
        Text(label, color = OnsightNavy, fontWeight = FontWeight.ExtraBold, fontSize = if (large) 14.sp else 10.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}
