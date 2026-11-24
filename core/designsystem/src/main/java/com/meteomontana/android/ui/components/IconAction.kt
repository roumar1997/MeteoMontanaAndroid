package com.meteomontana.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Boton de icono de la cabecera de Escuelas y de la ficha: lo mismo que un icono de la barra de pestanas de abajo
 * (icono relleno de 20 dp, tinta `onSurfaceVariant`), sin fondo, sin borde y sin texto. Encendido ([active]): color
 * primario (terracota), como la pestana activa. Area de toque de 44 dp como minimo. [description] es el nombre que
 * leen los lectores de pantalla (y los flujos de Maestro).
 *
 * [iconContent] permite un icono propio (la oruga, el progreso de descarga). [badge] > 0 pinta el globo rojo con el numero.
 */
@Composable
fun IconAction(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    badge: Long = 0,
    enabled: Boolean = true,
    activeColor: Color? = null,
    iconContent: (@Composable (tint: Color) -> Unit)? = null
) {
    val tint = if (active) (activeColor ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .height(44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val glyph: @Composable () -> Unit = {
            if (iconContent != null) iconContent(tint)
            else if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (badge > 0) {
            BadgedBox(badge = {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(if (badge > 9) "9+" else badge.toString(), color = Color.White)
                }
            }) { glyph() }
        } else glyph()
    }
}

/**
 * Fila de [count] botones de icono del mismo ancho, repartidos por todo el ancho disponible. Si a ese reparto no le
 * cabe el area de toque minima ([minItem], pantalla estrecha), la fila se desliza en horizontal; nunca se encoge el
 * area de toque. [content] recibe el modificador de ancho que hay que pasar a cada boton.
 */
@Composable
fun IconActionRow(
    count: Int,
    modifier: Modifier = Modifier,
    minItem: Dp = 44.dp,
    content: @Composable RowScope.(item: Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fair = maxWidth / count.coerceAtLeast(1)
        val width = if (fair < minItem) minItem else fair
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Start) {
            content(Modifier.width(width))
        }
    }
}
