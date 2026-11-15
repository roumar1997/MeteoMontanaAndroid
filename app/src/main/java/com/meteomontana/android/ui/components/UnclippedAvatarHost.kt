package com.meteomontana.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset

/**
 * Avatar con efecto de título (dragones, hadas) que NO se corta al hacer scroll. Un contenedor con scroll recorta
 * todo lo que se sale de su borde superior, y el efecto se partía con una línea recta. Aquí el avatar se dibuja en
 * una capa POR ENCIMA del scroll (fuera de su recorte) y sigue la posición de su hueco, como en iOS.
 *
 * @param slotOffsetPx posición Y del hueco dentro del host (se lee al colocar, sin recomponer); null = fuera de vista.
 * @param avatar el avatar con su aureola; se centra arriba.
 * @param content el contenido con scroll; debe reservar un hueco de la altura recibida donde iría el avatar.
 */
@Composable
fun UnclippedAvatarHost(
    slotOffsetPx: () -> Int?,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (slotHeight: Dp) -> Unit
) {
    var heightPx by remember { mutableIntStateOf(0) }
    Box(modifier) {
        content(with(LocalDensity.current) { heightPx.toDp() })
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, slotOffsetPx() ?: OFF_SCREEN_PX) }
                .onSizeChanged { heightPx = it.height }
        ) { avatar() }
    }
}

private const val OFF_SCREEN_PX = -100_000
