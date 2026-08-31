package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Grupo de acciones de cabecera dentro de una pastilla.
 *
 * **Por qué existe.** En iOS los botones de la cabecera no flotan sueltos sobre
 * el fondo: van agrupados en pastillas —una para el "atrás" y otra para las
 * acciones—. En Android estaban repartidos a lo ancho de la pantalla, y era de
 * las cosas que más delataban que no eran la misma app (lo señaló Rodrigo
 * comparando capturas).
 *
 * **Un solo sitio, a propósito.** Las cabeceras no repiten fondo, borde ni
 * forma: piden una pastilla y meten dentro sus iconos. Si mañana cambia el
 * aspecto —más redondeado, con sombra, translúcido— se toca aquí y cambian
 * todas a la vez. Es la misma idea que [CumbreChrome] para la barra de
 * pestañas: la decisión visual vive en un componente, no esparcida por las
 * pantallas.
 *
 * **Adaptable**: no fija ancho. Se encoge a lo que ocupen sus hijos, así que
 * una cabecera con dos iconos y otra con cinco funcionan igual, y en una
 * pantalla estrecha no desborda porque quien la usa la mete en una fila
 * flexible.
 */
@Composable
fun CumbrePillGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            // Ajustado para que el grupo quede a la altura de un IconButton
            // (48dp) sin engordarlo: el propio IconButton ya trae su margen.
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        content = content
    )
}
