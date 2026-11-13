package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.guide.pulseRing
import com.meteomontana.android.ui.theme.InterFamily
import com.meteomontana.android.ui.theme.Terra

/** Papel de las baldosas: el mismo que el de las tarjetas de escuelas (espejo de iOS 0xFBFAF6 / 0x211F19). */
@Composable
internal fun tileBackground(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFFBFAF6) else Color(0xFF211F19)

/**
 * Baldosa de acción (estilo «Baldosas» elegido por Álvaro): rectángulo redondeado del papel y borde fino de las
 * tarjetas de escuelas, icono de línea arriba y el nombre debajo (Inter 600, 10,5 sp, tinta). Encendida ([active]):
 * icono, texto y borde en terracota. El ancho lo pone quien la usa (todas las de una fila miden lo mismo, ver
 * [ActionTileRow]); el alto es fijo para que todas queden alineadas.
 *
 * [iconContent] permite un icono propio (la oruga, el progreso de descarga). [badge] > 0 pinta el globo con el número.
 */
@Composable
fun ActionTile(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = label,
    active: Boolean = false,
    badge: Long = 0,
    enabled: Boolean = true,
    pulsing: Boolean = false,
    activeColor: Color? = null,
    iconContent: (@Composable (tint: Color) -> Unit)? = null
) {
    val accent = activeColor ?: Terra
    val tint = if (active) accent else MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(52.dp)
            .then(if (pulsing) Modifier.pulseRing() else Modifier)
            .clip(shape)
            .background(tileBackground())
            .border(1.dp, if (active) accent else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val glyph: @Composable () -> Unit = {
            if (iconContent != null) iconContent(tint)
            else if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (badge > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(if (badge > 9) "9+" else badge.toString(), color = Color.White)
                    }
                }) { glyph() }
            } else glyph()
        }
        if (label != null) {
            Text(
                label,
                color = tint,
                fontFamily = InterFamily, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                overflow = TextOverflow.Visible
            )
        }
    }
}

/**
 * Fila de [count] baldosas del mismo ancho, repartidas por todo el ancho disponible. Si a ese reparto no le cabe el
 * nombre (pantalla estrecha o letra grande: ancho mínimo [minTile]), la fila se desliza en horizontal; nunca se
 * encoge el texto. [content] recibe el modificador de ancho que hay que pasar a cada baldosa.
 */
@Composable
fun ActionTileRow(
    count: Int,
    modifier: Modifier = Modifier,
    minTile: Dp = 50.dp,
    gap: Dp = 6.dp,
    content: @Composable RowScope.(tile: Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fair = (maxWidth - gap * (count - 1).coerceAtLeast(0)) / count.coerceAtLeast(1)
        val width = if (fair < minTile) minTile else fair
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) { content(Modifier.width(width)) }
    }
}
