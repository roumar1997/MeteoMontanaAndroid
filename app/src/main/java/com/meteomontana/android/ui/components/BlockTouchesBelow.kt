package com.meteomontana.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Para toda capa que se apila ENCIMA de otra (pantalla que se abre sobre las pestañas, overlay a pantalla completa):
 * en Compose un `Box` con solo `background` NO frena los toques, y los de las zonas vacías llegan a lo que haya
 * debajo (el chat de una escuela abría las Notificaciones de la pantalla de Escuelas). Con un `pointerInput` en la
 * capa, los toques que no coge ningún hijo se quedan en ella. No consume nada: los botones de dentro siguen igual.
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}
