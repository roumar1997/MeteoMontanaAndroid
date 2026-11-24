package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Cierra el teclado y suelta el foco del campo que lo tenía.
 *
 * Hace falta porque muchas pantallas de Cumbre abren el destino como un
 * **overlay dentro de la misma composición** (el detalle de escuela, las fichas
 * de piedra, los chats): al no cambiar de pantalla de verdad, el buscador
 * conserva el foco y el teclado se queda tapando lo que acabas de abrir. En iOS
 * no pasa porque allí la navegación empuja una vista nueva y el sistema lo baja
 * solo.
 *
 * Uso: en cuanto el usuario ELIGE un resultado (navegar o seleccionar), llama a
 * esto antes de actuar. Vive aquí, y no copiado en cada pantalla, para que la
 * regla sea una sola y un buscador nuevo solo tenga que usarlo.
 */
@Composable
fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}

/**
 * Oculta el teclado en cuanto el usuario empieza a hacer scroll en la lista (paridad con
 * `.scrollDismissesKeyboard(.interactively)` de iOS). No consume el scroll.
 */
@Composable
fun Modifier.dismissKeyboardOnScroll(): Modifier {
    val dismiss = rememberKeyboardDismisser()
    val connection = remember(dismiss) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y != 0f) dismiss()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
